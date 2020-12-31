
import java.io.File;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class Agent {

	enum Gap {
		constant, linear, tie, sqrt
	}

	enum PrintMode {
		treejson, flatjson, time
	}

	enum Bias {
		A, B, T
	}

	enum Role {
		Main, Clock, Reserve, MCR, CR
	}

	Role role;
	Bias input, output;
	int bias;
	int counter, phase, exponent, minute, hour, sample;
	Set<Integer> opinions;
	private boolean full, assigned, init3, init9;

	final static Role role_unused = Role.MCR;
	final static int counter_unused = -1, exponent_unused = 1, minute_unused = -1, hour_unused = -1, sample_unused = 1;
	final static boolean full_unused = false;

	Agent(Bias b) {

		this.role = Role.MCR;
		this.phase = 0;
		this.input = b;
		switch (b) {
		case A:
			this.bias = 1;
			break;
		case B:
			this.bias = -1;
			break;
		default:
			this.bias = 0;
			break;
		}
		this.exponent = 0;
		this.counter = counter_unused;
		this.hour = hour_unused;
		this.minute = minute_unused;
		this.sample = sample_unused;
		this.opinions = new HashSet<>();
		this.full = this.assigned = false;
		this.init3 = true;
		this.init9 = true;

	}

	static int starting_range = 100;
	static double pvalue = 0.01;
	static int counter_constant = 5;
	static int k = 5;
	static int log_n;
	static int L;
	static int gap;
	static int n;
	static int agents_in_phase_nine;
	static Random rnd;
	static int A_holders, B_holders;
	private static final String newLine = System.getProperty("line.separator");
	static Gap gap_mode = Gap.linear; // CONSTANT, Linear, TIE, sqrt

	public static void start_simulation(Gap gap, PrintMode print_mode) {

		set_voters_count(gap);
		ArrayList<Agent> agents = init_population();

		long interaction_tikz = 0;
		long phase9_counter = 0;
		long phase2_counter = 0;
		long phase4_counter = 0;

		while (agents_in_phase_nine < n && phase9_counter < n * log_n) { // while all agents are in phase 9 and nlogn
																			// interactions
			// passed in that phase
			if (interaction_tikz % n == 0) { // take snapshot of the population
				switch (print_mode) {
				case flatjson:
					state_to_json_flat(agents, interaction_tikz, false);
					break;
				case treejson:
					state_to_json_tree(agents, interaction_tikz, false);
					break;
				case time:
					break;
				default:
					throw new IllegalArgumentException("Unexpected value: " + print_mode);
				}
			}
			interaction_tikz++;

			// pick 2 different agents uniformly at random
			int u_idx = rnd.nextInt(n);
			int v_idx = rnd.nextInt(n);
			while (u_idx == v_idx)
				v_idx = rnd.nextInt(n);

			Agent u = agents.get(u_idx);
			Agent v = agents.get(v_idx);

			max_Phase_Epidemic(u, v); // both agents must agree on the phase number
			switch (u.phase) {
			case 0:
				phase_0_Initialize_Roles(u, v);
				break;
			case 1:
				phase_1_Discrete_Averaging_Init(u, 1);
				phase_1_Discrete_Averaging_Init(v, 1);
				phase_1_Discrete_Averaging(u, v, 1);
				break;
			case 2:
				phase_2or9_Output_the_Consensus(u, v, 2);
				phase2_counter++;
				break;
			case 3:
				phase_3_Synchronized_Rational_Averaging_Init(u);
				phase_3_Synchronized_Rational_Averaging_Init(v);

				phase_3_Synchronized_Rational_Averaging(u, v);
				break;
			case 4:
				phase4_counter++;
				phase_4_Output_Tie(u);
				phase_4_Output_Tie(v);
				break;
			case 5:
				phase_5_Reserve_Sample_Level_One_Way(u, v);
				phase_5_Reserve_Sample_Level_One_Way(v, u);
				break;
			case 6:
				phase_6_Reserve_Splits_One_Way(u, v);
				phase_6_Reserve_Splits_One_Way(v, u);
				break;

			case 7:
				phase_7_High_Level_Minority_Elimination(u, v);
				break;
			case 8:
				phase_8_Low_Level_Minority_Elimination(u, v);
				break;
			case 9:
				phase9_counter++;
				if (u.init9) {
					u.opinions.clear();
					u.init9 = false;
				}
				if (v.init9) {
					v.opinions.clear();
					v.init9 = false;
				}
				phase_2or9_Output_the_Consensus(u, v, 9);
				break;
			}
			if (phase2_counter > 8 * n * log_n || phase4_counter > 8 * n * log_n) {
				break;
			}
		}

		switch (print_mode) {
		case flatjson:
			state_to_json_flat(agents, interaction_tikz, true);
			break;
		case treejson:
			state_to_json_tree(agents, interaction_tikz, true);
			break;
		case time:
			simple_print_to_file("time_n_tikz", interaction_tikz);
			break;
		default:
			throw new IllegalArgumentException("Unexpected value: " + print_mode);
		}

		println("-------END SIMULATION-------after:" + (interaction_tikz / (log_n * n)));
		agents.clear();
	}

	private static void state_to_json_flat(ArrayList<Agent> agents, long time, boolean endOfFile) {

		Gson prettyGson = new GsonBuilder().setPrettyPrinting().create();

		Map<State, Integer> population_state = new HashMap<>();

		for (Agent a : agents) {
			State s = create_state(a);
			if (population_state.containsKey(s)) {
				population_state.replace(s, population_state.get(s) + 1);
			} else {
				population_state.put(s, 1);
			}
		}

		List<List<Object>> output = new ArrayList<List<Object>>();
		for (State s : population_state.keySet()) {
			List<Object> entry = new ArrayList<>();
			entry.add(s.toMap());
			entry.add(population_state.get(s));
			output.add(entry);
		}

		String prettyJson = prettyGson.toJson(output);
//		System.out.println(prettyJson);
		try {
			print_to_file(prettyJson, "flat_json_" + gap_mode + "_gap", time, endOfFile);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static State create_state(Agent a) {
		State s = new State();
		s.phase = a.phase;
		s.bias = a.bias;
		s.role = a.role.toString();
		s.counter = a.counter;
		s.full = a.full;
		s.exponent = a.exponent;
		s.minute = a.minute;
		s.hour = a.hour;
		s.sample = a.sample;
		s.output = (a.output != null) ? a.output.toString() : "";
		return s;
	}

	private static void state_to_json_tree(ArrayList<Agent> agents, long time, boolean EndOfFlie) {

		Gson prettyGson = new GsonBuilder().setPrettyPrinting().create();

		Map<String, SubPopulation> interaction_subpop = new HashMap<String, SubPopulation>();

		SubPopulation subpop = new SubPopulation(0, Fieldname.phase);
		subpop.agents = agents;

		subpop.children = new HashMap<String, SubPopulation>();
		subpop.partition();
		interaction_subpop.put(String.valueOf(time), subpop);
		String prettyJson = prettyGson.toJson(interaction_subpop);
//		System.out.println(prettyJson);
		try {
			print_to_file(prettyJson, "tree_json_", time, EndOfFlie);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		rnd = new Random();
		for (int r = 1; r < 20; r++) {
			starting_range = starting_range * 2;
			n = rnd.nextInt(starting_range);
			log_n = (int) (Math.ceil(Math.log(n) / Math.log(2)));
			L = log_n;
			agents_in_phase_nine = 0;
			System.out.printf("----------------START SIMULATION FOR N = %d, Log(N) = %d---------------------- \n", n,
					log_n);

			PrintMode print_mode = PrintMode.flatjson;// flat or treeF

			for (Gap g : Gap.values()) {
				gap_mode = g;
				agents_in_phase_nine = 0;
				System.out.printf("----------------START SIMULATION FOR N = %d, Log(N) = %d---------------------- \n",
						n, log_n);

				start_simulation(gap_mode, print_mode);
			}
		}
	}

	private static void reset_counter(Agent a) {
		a.counter = counter_constant * log_n;
		a.init3 = true;
	}

	private static void max_Phase_Epidemic(Agent u, Agent v) {

		Agent small = u;
		Agent large = v;
		if (small.phase == large.phase) {
			return;
		}
		if (small.phase > large.phase) {
			small = v;
			large = u;
		}

		small.phase = large.phase;
		small.init3 = true;
		if (small.phase == 9) {
			agents_in_phase_nine++;
		}
		if (isClock(small)) {
			reset_counter(small);
		} else {
			small.counter = counter_unused;
		}
	}

	private static void phase_8_Low_Level_Minority_Elimination(Agent u, Agent v) {

		if (isMain(u) && isMain(v) && have_Different_Bias(u, v)) {
			if (u.exponent > v.exponent && !u.full) {
				u.full = true;
				v.bias = 0;
			} else if (u.exponent < v.exponent && !v.full) {
				v.full = true;
				u.bias = 0;
			}
		}

		if (isClock(u)) {
			standard_Counter_Subroutine(u, 8);
		}
		if (isClock(v)) {
			standard_Counter_Subroutine(v, 8);
		}
	}

	private static void phase_7_High_Level_Minority_Elimination(Agent u, Agent v) {

		if (isMain(u) && isMain(v) && have_Different_Bias(u, v)) {
			if (u.exponent == v.exponent) {
				u.bias = v.bias = 0;
				u.exponent = v.exponent = 0;
			} else {
				Agent small = u;
				Agent large = v;
				if (small.exponent > large.exponent) {
					small = v;
					large = u;
				}
				if (large.exponent == small.exponent + 1) { // difference of one
					large.exponent = large.exponent - 1;
					small.bias = 0;
				} else if (large.exponent == small.exponent + 2) { // difference of two
					small.bias = large.bias;
					large.exponent = large.exponent - 1;
				}
			}
		}
		if (isClock(u)) {
			standard_Counter_Subroutine(u, 7);
		}
		if (isClock(v)) {
			standard_Counter_Subroutine(v, 7);
		}
	}

	private static void phase_6_Reserve_Splits_One_Way(Agent u, Agent v) {

		if (isReserve(u) && isMain(v) && (isA(v) || isB(v))) {
			if (u.sample < v.exponent && u.sample != sample_unused) {
				u.role = Role.Main;
				u.sample = sample_unused;
				u.bias = v.bias;
				u.exponent = v.exponent = Math.max(-L, v.exponent - 1);
			}
		}

		if (isClock(u)) {
			standard_Counter_Subroutine(u, 6);
		}
	}

	private static void phase_5_Reserve_Sample_Level_One_Way(Agent u, Agent v) {

		if (isReserve(u) && isMain(v) && (isA(v) || isB(v))) {
			if (u.sample == sample_unused) {
				u.sample = v.exponent;
			}
		}

		if (isClock(u)) {
			standard_Counter_Subroutine(u, 5);
		}
	}

	private static void phase_4_Output_Tie(Agent a) {// bug

		a.output = Bias.T;
		if (isMain(a) && Math.abs(a.exponent) < Math.abs(L) && a.exponent != 0 && a.bias != 0) {
			a.phase = 5;
		}
	}

	private static void phase_3_Synchronized_Rational_Averaging_Init(Agent u) {
		if (u.init3) {
			if (Math.abs(u.bias) > 1) {
				System.err.println("SLOW BACKUP Phase 3");
			}
			if (isMain(u) && u.bias == 0) {
				u.hour = 0;
			}
			if (isClock(u)) {
				u.minute = 0;
			}
			u.init3 = false;
		}
	}

	private static void phase_3_Synchronized_Rational_Averaging(Agent u, Agent v) {

		if (isClock(u) && isClock(v)) {
			if (u.minute == v.minute && u.minute < k * L) {
				if (rnd.nextDouble() < pvalue) {
					u.minute = v.minute = u.minute + 1;
				}
			} else if (u.minute != v.minute) {
				u.minute = v.minute = Math.max(u.minute, v.minute);
			}
		}
		if (isClock(u) && u.minute == k * L) {
			standard_Counter_Subroutine(u, 3);
		}
		if (isClock(v) && v.minute == k * L) {
			standard_Counter_Subroutine(v, 3);
		}
		if (u_is_Zero_v_is_Clock(u, v)) {
			u.hour = (int) Math.max(u.hour, Math.floor(v.minute / k));
		}
		if (v_is_Zero_u_is_Clock(u, v)) {
			v.hour = (int) Math.max(v.hour, Math.floor(u.minute / k));
		}

		if (isMain(u) && isMain(v)) {
			if (have_Different_Bias(u, v) && u.exponent == v.exponent) {
				u.bias = v.bias = 0;
				u.hour = v.hour = Math.abs(u.exponent);
				u.exponent = v.exponent = exponent_unused;
			}
			if (u.bias != 0)
				split_first_using_second(u, v);
			if (v.bias != 0)
				split_first_using_second(v, u);
		}
	}

	private static void standard_Counter_Subroutine(Agent a, int current_phase_number) {

		assert (a.counter > 0);
		assert (a.phase == current_phase_number);
		a.counter -= 1;
		if (a.counter == 0) {
			if (current_phase_number < 9)
				a.phase = current_phase_number + 1;
			if (a.phase == 9) { // terminate the simulation once all the agents are in phase 9
				agents_in_phase_nine++;
			}
			reset_counter(a);
		}
	}

	private static boolean u_is_Zero_v_is_Clock(Agent u, Agent v) {

		return (isMain(u) && u.bias == 0 && isClock(v));
	}

	private static boolean v_is_Zero_u_is_Clock(Agent u, Agent v) {
		return (isMain(v) && v.bias == 0 && isClock(u));
	}

	private static void split_first_using_second(Agent u, Agent v) {
		if (isZero(v) && Math.abs(u.exponent) < v.hour) {// split
			v.bias = u.bias;
			v.exponent = u.exponent = Math.max(-L, u.exponent - 1);
			v.hour = hour_unused;
		}
	}

	private static void add_Self_Opinion(Agent a) {
		a.opinions.add(a.bias);
	}

	private static void phase_0_Initialize_Roles(Agent u, Agent v) {

		if (isMCR(u) && isMCR(v)) {
			u.role = Role.Main;
			u.bias += v.bias;
			v.role = Role.CR;
			v.bias = 0;
		}

		Agent i = null, j = null;
		if (isMCR(u) && isMain(v) && v.assigned == false) {
			i = u;
			j = v;
		} else if (isMCR(v) && isMain(u) && u.assigned == false) {
			i = v;
			j = u;
		}

		if (i != null) {
			j.assigned = true;
			j.bias += i.bias;

			i.role = Role.CR;
			i.bias = 0;
		}

		i = null;
		j = null;
		if (isMCR(u) && !isMain(v) && !isMCR(v) && v.assigned == false) {
			i = u;
			j = v;
		} else if (isMCR(v) && !isMain(u) && !isMCR(u) && u.assigned == false) {
			i = v;
			j = u;
		}

		if (i != null) {
			j.assigned = true;
			i.role = Role.Main;
		}

		if (isCR(u) && isCR(v)) {
			u.role = Role.Clock;
			u.counter = counter_constant * log_n;
			v.role = Role.Reserve;
		}
		if (isClock(u)) {
			standard_Counter_Subroutine(u, 0);
		}
		if (isClock(v)) {
			standard_Counter_Subroutine(v, 0);
		}
	}

	private static void phase_1_Discrete_Averaging_Init(Agent u, int phase_number) {
		if (isMCR(u)) {
			System.err.println("SLOW BACKUP phase 1");
		}
		if (isCR(u)) {
			u.role = Role.Reserve;
		}
		if (isClock(u)) {
			// reset counter is done during incrementing phase
		}
	}

	private static void phase_1_Discrete_Averaging(Agent u, Agent v, int phase_number) {
		if (isMain(u) && isMain(v)) {
			int sum = u.bias + v.bias;
			u.bias = sum / 2;
			v.bias = sum - u.bias;
		}
		if (isClock(u)) {
			standard_Counter_Subroutine(u, 1);
		}
		if (isClock(v)) {
			standard_Counter_Subroutine(v, 1);
		}
	}

	// phase 2 and 9 are the same except for their "phase_number" input value
	private static void phase_2or9_Output_the_Consensus(Agent u, Agent v, int phase_number) {

		add_Self_Opinion(u);
		add_Self_Opinion(v);
		u.opinions.addAll(v.opinions);
		v.opinions.addAll(u.opinions);

		// at this point u and v have the same existing bias sets
		if (u.opinions.contains(-1) && u.opinions.contains(1)) {
			if (phase_number == 2)
				u.phase = v.phase = phase_number + 1;
			if (phase_number == 9)
				System.err.println("SLOW BACKUP phase 9");
		} else if (u.opinions.contains(1)) {
			u.output = v.output = Bias.A;
		} else if (u.opinions.contains(-1)) {
			u.output = v.output = Bias.B;
		} else if (u.opinions.contains(0)) {
			u.output = v.output = Bias.T;
		}
	}

	private static boolean have_Different_Bias(Agent u, Agent v) {
		return (isA(u) && isB(v)) || (isA(v) && isB(u));
	}

	static boolean isReserve(Agent a) {
		return a.role == Role.Reserve;
	}

	static boolean isCR(Agent a) {
		return a.role == Role.CR;
	}

	static boolean isMCR(Agent a) {
		return a.role == Role.MCR;
	}

	static boolean isMain(Agent a) {
		return a.role == Role.Main;
	}

	static boolean isClock(Agent a) {
		return a.role == Role.Clock;
	}

	static boolean isA(Agent a) {
		return (isMain(a) && a.bias > 0);
	}

	static boolean isB(Agent a) {
		return (isMain(a) && a.bias < 0);
	}

	static boolean isZero(Agent a) {
		return (isMain(a) && a.bias == 0);
	}

	private static ArrayList<Agent> init_population() {

		ArrayList<Agent> agents = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			if (i < A_holders) {
				agents.add(new Agent(Bias.A));
			} else {
				agents.add(new Agent(Bias.B));
			}
		}
		return agents;
	}

	private static String set_voters_count(Gap gmode) {
		// w.l.o.g set A to be the majority
		String majority = "A";
		int delta = 0;
		switch (gmode) {
		case constant:
			delta = 1;
			break;
		case linear:
			delta = n / 10;
			break;
		case sqrt:
			delta = 10 * (int) (Math.sqrt(n));
			break;
		case tie:
			delta = 0;
			if (n % 2 == 1) {
				n++;
			}
			majority = "tie";
			break;
		default:
			throw new IllegalArgumentException("Unexpected value: " + gmode);
		}
		A_holders = n / 2;
		A_holders += delta;
		B_holders = n - A_holders;
		int gap = Math.abs(B_holders - A_holders);

		System.out.printf("------------------------------- GAP= %d ~ O(%.4f n) \n \n", gap, ((double) gap / n));

		System.out.printf("B holders: %d   A Holders: %d           --> Majority: %s", B_holders, A_holders, majority);
		println("");
		return majority;
	}

	public static void simple_print_to_file(String data_portion, long time) {

		String fileName = String.format("majority_%s_P_%f_K_%d_C%d.json", data_portion, pvalue, k, counter_constant);
		PrintWriter printWriter = null;
		File file = new File(fileName);
		try {
			if (!file.exists()) {
				file.createNewFile();
			}
			printWriter = new PrintWriter(new FileOutputStream(fileName, true));
			printWriter.write(n + "," + time + newLine);
		} catch (IOException ioex) {
			ioex.printStackTrace();
		} finally {
			if (printWriter != null) {
				printWriter.flush();
				printWriter.close();
			}
		}
	}

	public static void print_to_file(String prettyJson, String data_portion, long time, boolean endOfLine)

			throws IOException {

		String line = String.format("\"%d\":", time) + prettyJson;
		String fileName = String.format("majority_%s_N_%d_LOG_%d_P_%f_K_%d_C%d.json", data_portion, n, log_n, pvalue, k,
				counter_constant);
		PrintWriter printWriter = null;
		File file = new File(fileName);
		try {
			if (time == 0 || !file.exists()) {
				file.createNewFile();
				printWriter = new PrintWriter(new FileOutputStream(fileName, true));
				printWriter.write("{\"interactions\":{"); // manually adding {} to make the file one json object
			} else {
				printWriter = new PrintWriter(new FileOutputStream(fileName, true));
			}
			if (endOfLine) {
				printWriter.write(newLine + line + newLine + "}}");
			} else {
				printWriter.write(newLine + line + ",");
			}

		} catch (IOException ioex) {
			ioex.printStackTrace();
		} finally {
			if (printWriter != null) {
				printWriter.flush();
				printWriter.close();
			}
		}
	}

	public static void println(Object obj) {
		System.out.println(obj);
	}

	public static void print(Object obj) {

		System.out.print(obj);
	}

}

class State {
	public int phase, exponent, minute, hour, counter, sample, bias;
	public boolean full;
	public String role, output;

	@Override
	public boolean equals(Object obj) {
		// If the object is compared with itself then return true
		if (obj == this) {
			return true;
		}

		/*
		 * Check if the object is an instance of State or not "null instanceof [type]"
		 * also returns false
		 */
		if (!(obj instanceof State)) {
			return false;
		}

		// typecast the object to State so that we can compare fields
		State s = (State) obj;
		return toOrderedArray(this).equals(toOrderedArray(s));
	}

	public List<String> toOrderedArray(State s) {

		return Arrays.asList(role, String.valueOf(bias), output, String.valueOf(full), String.valueOf(counter),
				String.valueOf(phase), String.valueOf(exponent), String.valueOf(minute), String.valueOf(hour),
				String.valueOf(sample));
	}

	public Map<String, Object> toMap() { // change it to directly call each field
		Map<String, Object> name_value = new HashMap<>();
		name_value.put("phase", phase);
		name_value.put("exponent", exponent);
		name_value.put("minute", minute);
		name_value.put("hour", hour);
		name_value.put("counter", counter);
		name_value.put("sample", sample);
		name_value.put("role", role);
		name_value.put("bias", bias);
		name_value.put("output", output);
		name_value.put("full", full);

		return name_value;
	}

	@Override
	public int hashCode() {
		return toOrderedArray(this).hashCode();
	}
}
