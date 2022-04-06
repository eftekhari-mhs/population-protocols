
import java.io.File;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * 
 *
 * @author eftekhari-mhs
 * @since 2021-12-30
 */
public class MyFileWriter {
	public MyFileWriter() {

	}

	enum PrintMode {
		flatjson, signals, all_clocks
	}

	
	private static final String newLine = System.getProperty("line.separator");

	public static void json_writer(ArrayList<DynamicAgent> agents, long interaction_tikz, boolean endoffile,
			String change_mode, int population_size) {
		state_to_json_flat(agents, interaction_tikz, endoffile, change_mode, population_size);

	}

	private static void state_to_json_flat(ArrayList<DynamicAgent> agents, long time, boolean endOfFile,
			String change_mode, int population_size) {

		Gson prettyGson = new GsonBuilder().setPrettyPrinting().create();

		Map<State, Integer> population_state = new HashMap<>();

		for (DynamicAgent a : agents) {
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
			print_to_file(prettyJson, change_mode, time, endOfFile, population_size);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static State create_state(DynamicAgent a) {
		State s = new State();
		s.group = a.group;
		s.grv = a.grv;
		s.phase = a.phase;
		s.fmv = a.first_missing_v;
		s.estimate = a.estimate;
		return s;
	}

	public static void print_to_file(ArrayList<ArrayList<Integer>> datArrayList, String data_portion)
			throws IOException {
		PrintWriter pw = null;
		FileOutputStream fo = null;
		File file = null;
		String file_name = String.format("dynamic_%s.txt", data_portion);
		try {
			file = new File(file_name);
			pw = new PrintWriter(new FileOutputStream(file));
			fo = new FileOutputStream(file);
			int datList = datArrayList.size();
			for (int i = 0; i < datList; i++) {
				pw.write(datArrayList.get(i).toString() + "\n");
			}
		} finally {
			pw.flush();
			pw.close();
			fo.close();
		}
	}

	public static void print_to_file(String prettyJson, String data_portion, long time, boolean endOfLine,
			int population_size)

			throws IOException {

		String line = String.format("\"%d\":", time) + prettyJson;
		String fileName = String.format("dynamic_counting_%s_%d.json", data_portion, population_size);
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

}

class State {
	public int group, grv, estimate, fmv;
	public DynamicAgent.Phase phase;
	
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

		return Arrays.asList(String.valueOf(group), String.valueOf(grv), String.valueOf(estimate),
				String.valueOf(fmv), phase.toString());
	}

	public Map<String, Object> toMap() { // change it to directly call each field
		Map<String, Object> name_value = new HashMap<>();
		name_value.put("estimate", estimate);
		name_value.put("fmv", fmv);
		name_value.put("grv", grv);
		name_value.put("group", group);
		name_value.put("phase", phase);
		return name_value;
	}

	@Override
	public int hashCode() {
		return toOrderedArray(this).hashCode();
	}
}
