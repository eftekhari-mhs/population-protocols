import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;


enum Fieldname {
	phase, role, bias, level, counter, minute, hour
}

class SubPopulation {

	final static int max_depth = 6;
	Fieldname field_name;
	transient String field_value;
	transient int depth = 0;
	public int count;
	public Map<String, SubPopulation> children;
	public transient ArrayList<Agent> agents;

	public SubPopulation(int d, Fieldname field) {
		this.field_name = field;
		this.depth = d;
	}

	public void partition() { 
		if (agents != null) {
			count = agents.size();
			if (depth < max_depth) {
				for (Agent a : agents) {
					switch (depth) {
					case 0:
						field_value = String.valueOf(a.phase);
						field_name = Fieldname.phase;
						break;
					case 1:
						field_value = a.role.toString();
						field_name = Fieldname.role;
						break;
					case 2:
						field_value = a.bias.toString();
						field_name = Fieldname.bias;
						break;
					case 3:
						field_value = String.valueOf(a.level);
						field_name = Fieldname.level;
						break;
					case 4:
						field_value = String.valueOf(a.counter);
						field_name = Fieldname.counter;
						break;
					case 5:
						field_value = String.valueOf(a.hour);
						field_name = Fieldname.hour;
						break;
					case 6:
						field_value = String.valueOf(a.minute);
						field_name = Fieldname.minute;
						break;
					case 7:
						field_value = null;
						field_name = null;
						break;
					default:
						throw new IllegalArgumentException("Unexpected value: " + depth);
					}

					if (field_value != null) {
						if (children != null && children.containsKey(field_value)) {
							SubPopulation temp = children.get(field_value);
							temp.agents.add(a);
						} else {
							if (children == null) {
								children = new HashMap<String, SubPopulation>();
							}
							SubPopulation temp = new SubPopulation(depth + 1, field_name);
							temp.agents = new ArrayList<Agent>();
							temp.agents.add(a);
							children.put(field_value, temp);
						}
					}
				}
			}
		}
		if (children != null)
			for (Map.Entry<String, SubPopulation> child : children.entrySet()) {
				child.getValue().partition();
			}
	}

}