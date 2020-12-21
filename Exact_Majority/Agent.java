
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
		constant, random, tie
	}

	enum PrintMode {
		treejson, flatjson
	}

	enum Bias {
		A, B, T, None
	}

	enum Role {
		Main, Clock, Reserve, Unassigned
	}

	Role role;
	Bias bias, input, output;
	int counter, phase, level, minute, hour, sample;
	Set<Bias> existing_bias_set;
	private boolean full;

	final static Role role_unused = Role.Unassigned;
	final static Bias bias_unused = Bias.None;
	final static int counter_unused = -1, level_unused = -1, minute_unused = -1, hour_unused = -1, sample_unused = -1;
	final static boolean full_unused = false;

	Agent(Bias b) {

		this.role = Role.Main; // possible values = {Main, Clock, Reserve, Unassigned}
		this.phase = 0;
		this.input = b;
		this.bias = b;
		this.level = log_n;
		this.counter = counter_constant * log_n;
		this.hour = hour_unused;
		this.minute = minute_unused;
		this.sample = sample_unused;
		this.existing_bias_set = new HashSet<Agent.Bias>();
		this.full = false;

	}

	static int range_n = 50000;
	static double pvalue = 0.01;// 0.0001;
	static int counter_constant = 5;
	static int k = 2;
	static int log_n;
	static int gap;
	static int n;
	static int agents_in_phase_eight;
	static Random rnd;
	static int A_holders, B_holders;
	private static final String newLine = System.getProperty("line.separator");

	public static void start_simulation(Gap gap, PrintMode print_mode) {

		set_voters_count(rnd, gap);
		ArrayList<Agent> agents = init_population();

		long interaction_tikz = 0;
		long phase8_counter = 0;
		while (agents_in_phase_eight < n && phase8_counter < n * log_n) { // while all agents are in phase 8 and nlogn
																			// interactions passed in that phase
			if (interaction_tikz % n == 0) { // take snapshot of the population
				switch (print_mode) {
				case flatjson:
					state_to_json_flat(agents, interaction_tikz, false);
					break;
				case treejson:
					state_to_json_tree(agents, interaction_tikz, false);
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
				phase_1or8_Check_for_Consensus(u, v, 1);
				break;
			case 2:
				change_unassigned_to_T(u);
				change_unassigned_to_T(v);

				phase_2_Mass_Averaging(u, v);
				break;
			case 3:
				phase_3_Detect_Tie(u);
				phase_3_Detect_Tie(v);
				break;
			case 4:
				u.existing_bias_set.clear();
				v.existing_bias_set.clear();
				phase_4_Reserve_Sample_Level_One_Way(u, v);
				phase_4_Reserve_Sample_Level_One_Way(v, u);
				break;
			case 5:
				phase_5_Reserve_Splits_One_Way(u, v);
				phase_5_Reserve_Splits_One_Way(v, u);
				break;

			case 6:
				phase_6_High_Level_Minority_Elimination(u, v);
				break;
			case 7:
				phase_7_Low_Level_Minority_Elimination(u, v);
				break;
			case 8:
				phase8_counter++;
				phase_1or8_Check_for_Consensus(u, v, 8);
				break;
			}
//			max_Phase_Epidemic(u, v); // make sure if one agent increased phase, the other does the same
		}

		switch (print_mode) {
		case flatjson:
			state_to_json_flat(agents, interaction_tikz, true);
			break;
		case treejson:
			state_to_json_tree(agents, interaction_tikz, true);
			break;
		default:
			throw new IllegalArgumentException("Unexpected value: " + print_mode);
		}

		println("-------END SIMULATION-------after:" + (interaction_tikz / (log_n * n)));

	}

	private static void change_unassigned_to_T(Agent u) {
		if (isUnassigned(u)) {
			u.role = Role.Main;
			u.bias = Bias.T;
		}
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
			print_to_file(prettyJson, "flat_json", time, endOfFile);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static State create_state(Agent a) {
		State s = new State();
		s.phase = a.phase;
		s.bias = (a.bias != Bias.None) ? a.bias.toString() : "";
		s.role = a.role.toString();
		s.counter = a.counter;
		s.full = a.full;
		s.level = a.level;
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
		for (int r = 0; r < 40; r++) {
			range_n = range_n * 2;
			n = rnd.nextInt(range_n);
			log_n = (int) (Math.ceil(Math.log(n) / Math.log(2)));
			agents_in_phase_eight = 0;
			System.out.printf("----------------START SIMULATION FOR N = %d, Log(N) = %d---------------------- \n", n,
					log_n);
//			Gap gap_mode = Gap.constant; // CONSTANT, RANDOM, TIE
			Gap gap_mode = Gap.random; // CONSTANT, RANDOM, TIE
			PrintMode print_mode = PrintMode.flatjson;// flat or tree

			start_simulation(gap_mode, print_mode);

		}

	}

	private static void reset_counter(Agent a) {
		a.counter = counter_constant * log_n;
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
		if (small.phase == 8) {
			agents_in_phase_eight++;
		}
		if (isClock(small)) {
			reset_counter(small);
		} else {
			small.counter = counter_unused;
		}
	}

	private static void phase_7_Low_Level_Minority_Elimination(Agent u, Agent v) {

		if (isMain(u) && isMain(v) && have_Different_Bias(u, v)) {
			if (u.level > v.level && !u.full) {
				u.full = true;
				v.bias = Bias.T;
			} else if (u.level < v.level && !v.full) {
				v.full = true;
				u.bias = Bias.T;
			}
		}

		if (isClock(u)) {
			standard_Counter_Subroutine(u, 7);
		}
		if (isClock(v)) {
			standard_Counter_Subroutine(v, 7);
		}
	}

	private static void phase_6_High_Level_Minority_Elimination(Agent u, Agent v) {

		if (isMain(u) && isMain(v) && have_Different_Bias(u, v)) {
			if (u.level == v.level) {
				u.bias = v.bias = Bias.T;
			} else {
				Agent small = u;
				Agent large = v;
				if (small.level > large.level) {
					small = v;
					large = u;
				}
				if (large.level == small.level + 1) { // difference of one
					large.level = large.level - 1;
					small.bias = Bias.T;
				} else if (large.level == small.level + 2) { // difference of two
					small.bias = large.bias;
//					small.level = large.level - 2; //small.level is equal to large.level-2 so we can leave it
					large.level = large.level - 1;
				}
			}
		}
		if (isClock(u)) {
			standard_Counter_Subroutine(u, 6);
		}
		if (isClock(v)) {
			standard_Counter_Subroutine(v, 6);
		}
	}

	private static void phase_5_Reserve_Splits_One_Way(Agent u, Agent v) {

		if (isReserve(u) && isMain(v) && (isA(v) || isB(v))) {
			if (u.sample < v.level && u.sample != sample_unused) {
				u.role = Role.Main;
				u.sample = sample_unused;
				u.bias = v.bias;
				u.level = v.level = Math.max(0, v.level - 1);
			}
		}

		if (isClock(u)) {
			standard_Counter_Subroutine(u, 5);
		}
	}

	private static void phase_4_Reserve_Sample_Level_One_Way(Agent u, Agent v) {

		if (isReserve(u) && isMain(v) && (isA(v) || isB(v))) {
			if (u.sample == sample_unused) {
				u.sample = v.level;
				u.bias = Bias.T; // TODO is this ok?
			}
		}

		if (isClock(u)) {
			standard_Counter_Subroutine(u, 4);
		}
	}

	private static void phase_3_Detect_Tie(Agent a) {
		a.output = Bias.T;
		if (isMain(a) && a.level > 0) {
			a.phase = 4;
		}
	}

	private static void phase_2_Mass_Averaging(Agent u, Agent v) {

		if (isClock(u) && isClock(v)) {
			if (u.minute == v.minute && u.minute > 0) {
				if (rnd.nextDouble() < pvalue) {
					u.minute = u.minute - 1;
				}
			} else if (u.minute != v.minute) {
				u.minute = v.minute = Math.min(u.minute, v.minute);
			} else if (u.minute == v.minute && u.minute == 0) {
				standard_Counter_Subroutine(u, 2);
				standard_Counter_Subroutine(v, 2);
			}
		}
		if (u_is_T_v_is_Clock(u, v)) {
			u.hour = (int) Math.min(u.hour, Math.ceil(v.minute / k));
		}
		if (v_is_T_u_is_Clock(u, v)) {
			v.hour = (int) Math.min(v.hour, Math.ceil(u.minute / k));
		}

		if (isMain(u) && isMain(v)) {
			if (have_Different_Bias(u, v) && u.level == v.level) {
				u.bias = v.bias = Bias.T;
				u.hour = v.hour = u.level;
				u.level = v.level = level_unused;
			}
			if (u.level > 0)
				split_first_using_second(u, v);
			if (v.level > 0)
				split_first_using_second(v, u);
		}
	}

	private static void standard_Counter_Subroutine(Agent a, int current_phase_number) {
		assert (a.counter > 0);
		assert (a.phase == current_phase_number);
		a.counter -= 1;
		if (a.counter == 0) {
			if (current_phase_number < 8)
				a.phase = current_phase_number + 1;
			if (a.phase == 8) { // terminate the simulation once all the agents are in phase 8
				agents_in_phase_eight++;
			}
			reset_counter(a);
		}
	}

	private static boolean u_is_T_v_is_Clock(Agent u, Agent v) {
		return (isMain(u) && isT(u) && isClock(v));
	}

	private static boolean v_is_T_u_is_Clock(Agent u, Agent v) {
		return (isMain(v) && isT(v) && isClock(u));
	}

	private static void split_first_using_second(Agent u, Agent v) {
		if ((isA(u) || isB(u)) && isT(v) && u.level > v.hour) {// split
			v.bias = u.bias;
			v.level = u.level = Math.max(0, u.level - 1);
			v.hour = hour_unused;
		}
	}

	private static void add_Self_Bias(Agent a) {

		if (isA(a)) {
			a.existing_bias_set.add(Bias.A);
		}
		if (isB(a)) {
			a.existing_bias_set.add(Bias.B);
		}
	}

	private static void phase_0_Initialize_Roles(Agent u, Agent v) {

		if (isMain(u) && isMain(v) && have_Different_Bias(u, v)) {
			u.role = Role.Unassigned;
			v.role = Role.Reserve;
			init_reserve(v);
			u.bias = v.bias = Agent.bias_unused; // not in the pseudocode
		}
		if (isUnassigned(u) && isUnassigned(v)) {
			u.role = Role.Clock;
			init_clock(u);
			v.role = Role.Main;
			init_main_T(v);
		}
		standard_Counter_Subroutine(u, 0);
		standard_Counter_Subroutine(v, 0);
	}

	private static void init_main_T(Agent u) {
		u.hour = log_n;
		u.bias = Bias.T;
		u.level = level_unused;
		u.minute = minute_unused;
	}

	private static void init_reserve(Agent u) {
		u.level = level_unused;
		u.bias = bias_unused;
		u.minute = minute_unused;
		u.hour = hour_unused;
	}

	private static void init_clock(Agent u) {
		u.bias = bias_unused;
		u.level = level_unused;
		u.minute = k * log_n;
		u.hour = hour_unused;
	}

	// phase 1 and 8 are the same except for their "phase_number" input value
	private static void phase_1or8_Check_for_Consensus(Agent u, Agent v, int phase_number) {

		add_Self_Bias(u);
		add_Self_Bias(v);
		u.existing_bias_set.addAll(v.existing_bias_set);
		v.existing_bias_set.addAll(u.existing_bias_set);

		// at this point u and v have the same existing bias sets
		if (u.existing_bias_set.contains(Bias.A) && u.existing_bias_set.contains(Bias.B)) {
			if (phase_number == 1)
				u.phase = v.phase = phase_number + 1;
			if (phase_number == 8)
				System.err.println("SLOW BACKUP");
		} else if (u.existing_bias_set.contains(Bias.A)) {
			u.output = v.output = Bias.A;
		} else if (u.existing_bias_set.contains(Bias.B)) {
			u.output = v.output = Bias.B;
		} else if (u.existing_bias_set.isEmpty()) {
			u.output = v.output = Bias.T;
		}
	}

	private static boolean have_Different_Bias(Agent u, Agent v) {
		return (isA(u) && isB(v)) || (isA(v) && isB(u));
	}

	static boolean isReserve(Agent a) {
		return a.role == Role.Reserve;
	}

	static boolean isMain(Agent a) {
		return a.role == Role.Main;
	}

	static boolean isClock(Agent a) {
		return a.role == Role.Clock;
	}

	static boolean isUnassigned(Agent a) {
		return a.role == Role.Unassigned;
	}

	static boolean isA(Agent a) {
		return (isMain(a) && a.bias == Bias.A);
	}

	static boolean isB(Agent a) {
		return (isMain(a) && a.bias == Bias.B);
	}

	static boolean isT(Agent a) {
		return (isMain(a) && a.bias == Bias.T);
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

	private static String set_voters_count(Random r, Gap gmode) {

		int delta = 0;
		switch (gmode) {
		case constant:
			delta = 1;
			break;
		case random:
			delta = rnd.nextInt(n / 10) + 1; // TODO set it to fixed numbers log (n), sqrt(n), ...?
			break;
		case tie:
			delta = 0;
			if (n % 2 == 1) {
				n++;
			}
			break;
		default:
			throw new IllegalArgumentException("Unexpected value: " + gmode);
		}
		A_holders = n / 2;
		String majority = "";
		if (rnd.nextBoolean()) {
			A_holders += delta;
			majority = "A";
		} else {
			A_holders -= delta;
			majority = "B";
		}
		B_holders = n - A_holders;
		int gap = Math.abs(B_holders - A_holders);

		System.out.printf("------------------------------- GAP= %d ~ O(%.4f n) \n \n", gap, ((double) gap / n));

		System.out.printf("B holders: %d   A Holders: %d           --> Majority: %s", B_holders, A_holders, majority);
		println("");
		return majority;
	}

	public static void print_to_file(String prettyJson, String data_portion, long time, boolean endOfLine)

			throws IOException {

		String line = String.format("\"%d\":", time) + prettyJson;
		String fileName = String.format("majority_%s_N_%d_LOG_%d_P_%f_K_%d.json", data_portion, n, log_n, pvalue, k);
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
	public int phase, level, minute, hour, counter, sample;
	public boolean full;
	public String role, bias, output;
//	public Set<Agent.Bias> existing_bias_set;

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

		return Arrays.asList(role, bias, output, String.valueOf(full), String.valueOf(counter), String.valueOf(phase),
				String.valueOf(level), String.valueOf(minute), String.valueOf(hour), String.valueOf(sample));
	}

	public Map<String, Object> toMap() { // change it to directly call each field
		Map<String, Object> name_value = new HashMap<>();
		name_value.put("phase", phase);
		name_value.put("level", level);
		name_value.put("minute", minute);
		name_value.put("hour", hour);
		name_value.put("counter", counter);
		name_value.put("sample", sample);
		name_value.put("role", role);
		name_value.put("bias", bias);
		name_value.put("output", output);
		name_value.put("full", full);

//		for (Field f : State.class.getFields()) {
//			String name = f.getName();
//			String value = "";
//			try {
//				value = (String) f.get(this);
//			} catch (IllegalArgumentException | IllegalAccessException e) {
//				e.printStackTrace();
//			}
//			name_value.put(name, value);
//		}
		return name_value;

//		Map<String, String> name_value = new HashMap<String, String>();
//		for (Field f : State.class.getFields()) {
//			String name = f.getName();
//			String value = "";
//			try {
//				value = (String) f.get(this);
//			} catch (IllegalArgumentException | IllegalAccessException e) {
//				e.printStackTrace();
//			}
//			name_value.put(name, value);
//		}
//		return name_value;
	}

	@Override
	public int hashCode() {
		return toOrderedArray(this).hashCode();
	}
}
