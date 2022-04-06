import java.io.IOException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class DynamicAgent {

	enum dec_mode {
		randomly, min, max, drastic
	}

	enum change_mode {
		inc, dec
	}

	enum Phase {
		normal, waiting, updating
	}

	ArrayList<Integer> all_clocks;
	int estimate;
	int group, first_missing_v;
	int grv;
	int timer_constant = 4;
	int log_n_clocks_bound = 40;
	int timer;
	Phase phase;

	public DynamicAgent() {
		this.group = 1;
		this.all_clocks = new ArrayList<Integer>();
		for (int i = 0; i < log_n_clocks_bound; i++) {
			this.all_clocks.add(0);
		}
		this.phase = Phase.normal;
	}

	public DynamicAgent(Random r) {
		this.estimate = r.nextInt(100);
		this.group = r.nextInt(log_n_clocks_bound / 2);
		this.all_clocks = new ArrayList<Integer>();
		for (int i = 0; i < log_n_clocks_bound; i++) {
			if (i == 0)
				this.all_clocks.add(0);
			else
				this.all_clocks.add(r.nextInt(3 * i + 1));
		}
		switch (r.nextInt(3)) {
		case 0:
			this.phase = Phase.normal;
			break;
		case 1:
			this.phase = Phase.waiting;
			break;
		case 2:
			this.phase = Phase.updating;
			break;

		default:
			throw new IllegalArgumentException("Unexpected value: " + r.nextInt(3));
		}
	}

	public static int rounds_of_computations = 16;
	public static int population_size;
	public static int size_range = 800000;
	public static int logn;
	public static Random rnd;
	public static change_mode cm = change_mode.dec;

	public static void println(Object obj) {
		System.out.println(obj);
	}

	public static void print(Object obj) {
		System.out.print(obj);
	}

	public static void start_simulation() {
		int org_population_size = population_size;
		ArrayList<DynamicAgent> agents = new ArrayList<DynamicAgent>();
		ArrayList<ArrayList<Integer>> existing_signal_values = new ArrayList<ArrayList<Integer>>();
		ArrayList<ArrayList<Integer>> existing_clock_signals = new ArrayList<ArrayList<Integer>>();
		for (int i = 0; i < population_size; i++) {
//			agents.add(new DynamicAgent(rnd));
			agents.add(new DynamicAgent());
		}
//		set_all_regular_grvs(agents, 0); // error = 0;
		long interaction_tikz = 0;

		for (int r = 0; r < rounds_of_computations; r++) {

			int threshold = population_size * logn * 8;
			for (int t = 0; t < threshold; t++) {
				interaction_tikz++;
				if (t % population_size == 0) {
					existing_signal_values.add(aggregate_group_distribution(agents));
					existing_clock_signals.add(aggregate_pairwise_minimum_signal_array(agents));
					MyFileWriter.json_writer(agents, interaction_tikz, false, cm.toString(), org_population_size);
				}
				// pick 2 different agents uniformly at random
				int rec_idx = rnd.nextInt(population_size);
				int sen_idx = rnd.nextInt(population_size);
				while (rec_idx == sen_idx)
					sen_idx = rnd.nextInt(population_size);

				DynamicAgent sen = agents.get(sen_idx);
				DynamicAgent rec = agents.get(rec_idx);

				interact(rec, sen);
			}

			println("end of round " + r);

			println(agents.get(0).all_clocks);
			print_all_existing_estimates(agents);
			if (r == 8)
				adversary(agents);
		}

		MyFileWriter.json_writer(agents, interaction_tikz, true, cm.toString(), org_population_size);
		try {
			MyFileWriter.print_to_file(existing_signal_values, "clock_inits_" + org_population_size);
			MyFileWriter.print_to_file(existing_clock_signals, "clock_signals_" + org_population_size);
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	public static void main(String[] args) {

		rnd = new Random();
		population_size = 400000;// rnd.nextInt(size_range);
		logn = (int) (Math.log(population_size) / Math.log(2));
		println("n: " + population_size + "   log n: " + logn);
		start_simulation();
	}

	// top-level interaction code
	public static void interact(DynamicAgent u, DynamicAgent v) {
		updating_group(u, v);
		reset_count_down_on_array(u, v);
		for (DynamicAgent a : new DynamicAgent[] { u, v }) {
			update_missing_value(a);
			timer_routine(a);
			if (a.phase == Phase.normal) {
				size_checker(a);
			}
		}

		if (u.phase == Phase.updating && v.phase == Phase.updating) {
			propagate_max_grv(u, v);
		} else if (u.phase == Phase.normal && v.phase == Phase.normal) {
			// both confident that their values are correct
			propagate_max_grv(u, v);
			u.estimate = u.grv;
			v.estimate = v.grv;
		}
	}

	private static void size_checker(DynamicAgent u) {
		if (u.first_missing_v *(1.5) < u.estimate) {
			u.phase = Phase.waiting;
		}
		if (u.first_missing_v /4 > u.estimate) {
			u.phase = Phase.waiting;
		}
	}

	private static void print_all_existing_estimates(ArrayList<DynamicAgent> agents) {
		Set<Integer> outputs = new HashSet<Integer>();
		for (DynamicAgent a : agents) {
			outputs.add(a.estimate);
		}

		System.err.println("output values: " + outputs);
	}

	public static void adversary(ArrayList<DynamicAgent> agents) {
		if (cm == change_mode.inc) {
			print("incrementing population size. ");
			inc_the_population_size(agents);
		} else if (cm == change_mode.dec) {
			print("decrementing population size. ");
			dec_the_population_size(agents, dec_mode.drastic);
		}
	}

	private static void dec_the_population_size(ArrayList<DynamicAgent> agents, dec_mode mode) {
		int dead_agents = rnd.nextInt(population_size);
		population_size -= dead_agents;
		logn = (int) (Math.log(population_size) / Math.log(2));
		if (mode == dec_mode.randomly) {
			for (int i = 0; i < dead_agents; i++) {
				agents.remove(0);
			}
			println("updated* n: " + population_size + "   log n: " + logn);
		}

		if (mode == dec_mode.min) {
			int min = 1;
			int d = dead_agents;
			while (d > 0) {
				for (int j = 0; j < agents.size(); j++) {
					if (d > 0 && agents.get(j).group <= min) {
						agents.remove(j);
						d--;
					}
				}
				min++;
			}
			println("updated* n: " + population_size + "   log n: " + logn);
		}

		if (mode == dec_mode.max) {
			int max = logn;
			int d = dead_agents;
			while (d > 0) {
				for (int j = 0; j < agents.size(); j++) {
					if (d > 0 && agents.get(j).group >= max) {
						agents.remove(j);
						d--;
					}
				}
				max--;
			}
			println("updated* n: " + population_size + "   log n: " + logn);
		}

		if (mode == dec_mode.drastic) {
			if (population_size > 10000) {
				population_size = 500;
			} else if (population_size > 200) {
				population_size = 50;
			}
			logn = (int) (Math.log(population_size) / Math.log(2));
			ArrayList<DynamicAgent> new_agents = new ArrayList<DynamicAgent>();
			for (int j = 0; j < population_size; j++) {
				new_agents.add(agents.get(j));
			}
			agents.clear();
			agents.addAll(new_agents);
			println("updated* n: " + population_size + "   log n: " + logn);
		}
	}

	private static void propagate_max_grv(DynamicAgent u, DynamicAgent v) {
		u.grv = v.grv = Math.max(u.grv, v.grv);

	}

	static void timer_routine(DynamicAgent u) {
		int threshold = 4 * u.first_missing_v;

		if (u.phase != Phase.normal) {
			u.timer++;
		}
		if (u.timer >= threshold) { // timer hits the threshold
			if (u.phase == Phase.waiting) {
				// every agent must have almost equal "first_missing_value"
				// thus, we can use it as a mutual timer bound
				u.grv = generate_regular_grv();
				u.timer = 1;
				u.phase = Phase.updating;
			} else if (u.phase == Phase.updating) {
				u.estimate = u.grv;
				u.phase = Phase.normal;
				u.timer = 0;
				// must wait for some inconsistency to trigger internal_timer
			}
		}
	}

	private static void updating_group(DynamicAgent u, DynamicAgent v) {
		if (rnd.nextBoolean())
			u.group += 1;
		else {
			v.group = 1;
		}
	}

	private static void update_missing_value(DynamicAgent u) {
		// looking for the first zero
		boolean zeros = false;
//		int loglogn = (int) (Math.log(Math.log(population_size)/Math.log(2))/Math.log(2));
		int start = 2;
		if (u.estimate > 10)
			start = (int) Math.floor((1 + Math.log(u.estimate)) / Math.log(2));

		for (int i = start; i < u.all_clocks.size(); i++) {
			if (u.all_clocks.get(i) == 0) {
				u.first_missing_v = i - 1;
				zeros = true;
				return;
			}
		}
		if (!zeros) {
			u.first_missing_v = u.all_clocks.size() - 1;
		}
	}

	private static void inc_the_population_size(ArrayList<DynamicAgent> agents) {
		int count_new_agents = rnd.nextInt(1000 * population_size);

		ArrayList<DynamicAgent> new_agents = new ArrayList<DynamicAgent>();
		for (int i = 0; i < count_new_agents; i++) {
			new_agents.add(new DynamicAgent());
		}

		population_size += count_new_agents;
		logn = (int) (Math.log(population_size) / Math.log(2));

//		set_all_regular_grvs(new_agents, 0); // error = 0;

		agents.addAll(new_agents);
		println("updated* n: " + population_size + "   log n: " + logn);
	}

	private static void reset_count_down_on_array(DynamicAgent u, DynamicAgent v) {
		ArrayList<Integer> clk_u = new ArrayList<Integer>();
		ArrayList<Integer> clk_v = new ArrayList<Integer>();
		clk_u = u.all_clocks;
		clk_v = v.all_clocks;

		// special case for direct contact
		for (int i = 1; i < clk_u.size(); i++) {
			int max_i = Math.max(clk_u.get(i), clk_v.get(i));
			clk_u.set(i, Math.max(0, max_i - 1));
			clk_v.set(i, Math.max(0, max_i - 1));
		}

		int c = u.timer_constant;
		// special case for direct contact
		clk_u.set(u.group, u.group * c);
		clk_v.set(u.group, u.group * c);
		clk_v.set(v.group, v.group * c);
		clk_u.set(v.group, v.group * c);
	}

	public static ArrayList<Integer> aggregate_group_distribution(ArrayList<DynamicAgent> agents) {
		ArrayList<Integer> clv = new ArrayList<Integer>();
//		clv.add(logn);
		int limit = agents.get(0).log_n_clocks_bound;

		for (int i = 1; i < limit; i++) {
			clv.add(0);
		}

		for (DynamicAgent a : agents) {
			clv.set(a.group, clv.get(a.group) + 1);
		}

		return clv;
	}

	public static ArrayList<Integer> aggregate_pairwise_minimum_signal_array(ArrayList<DynamicAgent> agents) {
		ArrayList<Integer> clv = new ArrayList<Integer>();
		clv.addAll(agents.get(0).all_clocks);

		for (DynamicAgent a : agents) {
			for (int i = 1; i < clv.size(); i++) {
				clv.set(i, Math.min(a.all_clocks.get(i), clv.get(i)));
			}
		}

//		clv.set(0, logn);
		return clv;
	}

	static int generate_regular_grv() {
		int g = 1;
		while (rnd.nextBoolean()) {
			g++;
		}
		return g;
	}

}
