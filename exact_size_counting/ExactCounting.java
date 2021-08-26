package composition;

import java.util.Random;

public class ExactCounting {

	public static int population_size = 7210;
	public static int cte = 20;
	public static int constant_pop_interactions_to_wait = 120;
	public static int constant_num_grv = 4;
	public static Random rnd;

	public static void println(Object obj) {
		System.out.println(obj);
	}

	public static void print(Object obj) {
		System.out.print(obj);
	}

	public static void main(String[] args) {
		rnd = new Random();

		AgentEC[] agents = new AgentEC[population_size];
		for (int i = 0; i < agents.length; i++) {
			agents[i] = new AgentEC();
		}

		int number_of_interactions = 0;

		while (true) {
			number_of_interactions++;
			int rec_idx = rnd.nextInt(population_size);
			int sen_idx = rnd.nextInt(population_size);
			while (rec_idx == sen_idx)
				sen_idx = rnd.nextInt(population_size);

			AgentEC sen = agents[sen_idx];
			AgentEC rec = agents[rec_idx];

			partition_into_A_and_F(sen, rec);

			if (is_A(sen))
				sen.time++;
			if (is_A(rec))
				rec.time++;

			if (estimationEpochs(sen) || estimationEpochs(rec)) {
				if (is_one_A_and_one_F(rec, sen)) {
					generate_clk(sen, rec);
					if (is_A_and_done_clk(rec) || is_A_and_done_clk(sen))
						generate_grv_for_size_estimation(sen, rec);
				} else if (is_A_and_done_clk(rec) && is_A_and_done_clk(sen)) {
					propagatemax_clk(sen, rec);
					if (sen.done_gr && rec.done_gr) {
						propagatemax_grv(sen, rec);
					}
				}
			}

			else if (LeaderElectionEpochs(sen) || LeaderElectionEpochs(rec)) {
				if (is_one_A_and_one_F(rec, sen) && is_A_agent_still_leader(rec, sen)) {
					generate_id(sen, rec);
				} else if (is_A_isLeader_and_done_id(rec) && is_A_isLeader_and_done_id(sen)) {
					propagatemax_id(sen, rec);
				}
			}

			else {
				if (is_leader_and_not_init(sen)) {
					sen.avg = (long) Math.pow(2, 2 * sen.gr);
					sen.init = true;
				}
				if (is_leader_and_not_init(rec)) {
					rec.avg = (long) Math.pow(2, 2 * rec.gr);
					rec.init = true;
				}
				averaging(sen, rec);
			}
			
			if (is_A_and_done_clk(rec) && is_A_and_done_clk(sen)) {
				check_if_timer_done_and_increment_epoch(sen);
				check_if_timer_done_and_increment_epoch(rec);
				propagate_incremented_epoch(sen, rec);
				propagate_incremented_epoch(rec, sen);
			}

			if (number_of_interactions > constant_pop_interactions_to_wait * Math.log(population_size)
					* Math.log(population_size) * population_size)
				break;
		}

		int number_of_A, number_of_F, number_of_leaders, min_estimation, max_estimation, min_clk, max_clk;
		min_clk = min_estimation = 9999;
		long minSize = Long.MAX_VALUE;
		long maxSize = 0;
		number_of_A = number_of_F = number_of_leaders = max_estimation = max_clk = 0;
		for (AgentEC agent : agents) {
			if (agent.leader)
				number_of_leaders++;
			if (agent.state == 'A')
				number_of_A++;
			if (agent.state == 'F')
				number_of_F++;
			if (is_A(agent)) {
				// print("generating clk status: " + (agent.done_clk) + ", ");
				// print("num grv's: " + (constant_num_grv * agent.clk) + ", ");
				// print("sum: " + (agent.sum) + ", ");
				// println("log estimate: " + (agent.sum / (constant_num_grv * agent.clk)));
				max_estimation = Math.max(agent.sum / (constant_num_grv * agent.clk), max_estimation);
				min_estimation = Math.min(agent.sum / (constant_num_grv * agent.clk), min_estimation);
				max_clk = Math.max(agent.clk, max_clk);
				min_clk = Math.min(agent.clk, min_clk);
				maxSize = (long) Math.max(Math.pow(2, 2 * agent.gr) / agent.avg, maxSize);
				minSize = (long) Math.min(Math.pow(2, 2 * agent.gr) / agent.avg, minSize);
			}
			// print("Leader bit : " + (agent.leader) + ", ");
			// if (agent.avg != 0)
			// print("size : " + (long) Math.pow(2, 2 * agent.gr) / agent.avg + ", ");
		}
		System.out.println();
		System.out.println("Number of leader agents: " + number_of_leaders);
		System.out.println("Number of agents in state A: " + number_of_A);
		System.out.println("Number of agents in state F: " + number_of_F);
		System.out.println("Min clk amongst the population: " + min_clk);
		System.out.println("Max clk amongst the population: " + max_clk);
		System.out.println("Actual Log of population size: " + Math.log(population_size));
		System.out.println("Min log size estimation amongst the population: " + min_estimation);
		System.out.println("Max log size estimation amongst the population: " + max_estimation);
		System.out.println("Min Population Size: " + minSize);
		System.out.println("Max Population Size: " + maxSize);
	}

	public static void averaging(AgentEC sen, AgentEC rec) {
		long temp = sen.avg + rec.avg;
		sen.avg = temp / 2;
		rec.avg = temp - sen.avg;
	}

	public static boolean is_A_agent_still_leader(AgentEC agent1, AgentEC agent2) {
		if (is_A(agent1))
			return agent1.leader;
		else if (is_A(agent2))
			return agent2.leader;
		return false;
	}

	public static boolean is_leader_and_not_init(AgentEC agent) {
		return agent.leader && !agent.init;
	}

	public static boolean estimationEpochs(AgentEC agent) {
		if (is_A(agent) && agent.epoch < constant_num_grv * agent.clk)
			return true;
		return false;
	}

	public static boolean LeaderElectionEpochs(AgentEC agent) {
		if (is_A(agent)
				&& (agent.epoch == constant_num_grv * agent.clk || agent.epoch == constant_num_grv * agent.clk + 1))
			return true;
		return false;
	}

	public static boolean is_one_A_and_one_F(AgentEC agent1, AgentEC agent2) {
		return is_A(agent1) && is_F(agent2) || is_A(agent2) && is_F(agent1);
	}

	public static boolean is_A_and_done_clk(AgentEC agent) {
		return is_A(agent) && agent.done_clk;
	}

	public static boolean is_A_isLeader_and_done_id(AgentEC agent) {
		if (is_A(agent) && !agent.leader)
			return true;
		return is_A(agent) && agent.done_id;
	}

	public static boolean is_A_and_done_grv_for_size_estimation(AgentEC agent) {
		return is_A(agent) && agent.done_gr;
	}

	public static boolean is_A(AgentEC agent) {
		return agent.state == 'A';
	}

	public static boolean is_F(AgentEC agent) {
		return agent.state == 'F';
	}

	public static void partition_into_A_and_F(AgentEC sen, AgentEC rec) {
		if (sen.state == 'X' && rec.state == 'X') {
			sen.state = 'A';
			sen.leader = true;
			rec.state = 'F';
			rec.leader = false;
		} else if (sen.state == 'A' && rec.state == 'X') {
			rec.state = 'F';
			rec.leader = false;
		} else if (sen.state == 'F' && rec.state == 'X') {
			rec.state = 'A';
			rec.leader = true;
		}
	}

	public static void generate_clk(AgentEC sen, AgentEC rec) {
		if (sen.state == 'A' && rec.state == 'F' && !sen.done_clk) {
			sen.clk++;
		} else if (sen.state == 'F' && rec.state == 'A') {
			rec.done_clk = true;
		}
	}

	public static void generate_id(AgentEC sen, AgentEC rec) {
		if (sen.state == 'A' && rec.state == 'F' && !sen.done_id) {
			sen.id = sen.id * 2 + 1;
			sen.id_length++;
			if (sen.id_length == (int) Math.log(2 * sen.clk)) {
				sen.done_id = true;
			}
		} else if (sen.state == 'F' && rec.state == 'A' && !rec.done_id) {
			rec.id = rec.id * 2;
			rec.id_length++;
			if (rec.id_length == (int) Math.log(2 * rec.clk))
				rec.done_id = true;
		}
	}

	public static void generate_grv_for_size_estimation(AgentEC sen, AgentEC rec) {
		if (sen.state == 'A' && rec.state == 'F' && !sen.done_gr) {
			sen.gr++;
		} else if (sen.state == 'F' && rec.state == 'A') {
			rec.done_gr = true;
		}
	}

	public static void propagatemax_clk(AgentEC sen, AgentEC rec) {
		if (rec.clk < sen.clk) {
			rec.clk = sen.clk;
			rec.leader = false;
			reset_size_estimation(rec);
		} else if (sen.clk < rec.clk) {
			sen.clk = rec.clk;
			sen.leader = false;
			reset_size_estimation(sen);
		}
	}

	public static void propagatemax_id(AgentEC sen, AgentEC rec) {
		if (rec.id < sen.id) {
			rec.id = sen.id;
			rec.leader = false;
		} else if (sen.id < rec.id) {
			sen.id = rec.id;
			sen.leader = false;
		}
	}

	public static void propagatemax_grv(AgentEC sen, AgentEC rec) {
		if (sen.gr != rec.gr)
			sen.gr = rec.gr = Math.max(sen.gr, rec.gr);
	}

	public static void reset_size_estimation(AgentEC agent) {
		agent.id_length = agent.time = agent.sum = agent.epoch = 0;
		agent.id = agent.avg = 0;
		agent.gr = 1;
		agent.done_gr = agent.init = agent.done_id = false;
	}

	public static void check_if_timer_done_and_increment_epoch(AgentEC agent) {
		if (agent.time > cte * agent.clk) {
			agent.epoch++;
			if (agent.epoch < constant_num_grv * agent.clk)
				move_to_next_grv_for_size_estimation(agent);
			else
				agent.time = 0;
		}
		if (agent.epoch == constant_num_grv * agent.clk)
			agent.gr = agent.sum / (constant_num_grv * agent.clk);
	}

	public static void propagate_incremented_epoch(AgentEC sen, AgentEC rec) {
		if (rec.epoch < sen.epoch) {
			rec.epoch = sen.epoch;
			if (rec.epoch < constant_num_grv * rec.clk)
				move_to_next_grv_for_size_estimation(rec);
			else
				rec.time = 0;
		} else if (sen.epoch < rec.epoch) {
			sen.epoch = rec.epoch;
			if (rec.epoch < constant_num_grv * rec.clk)
				move_to_next_grv_for_size_estimation(sen);
			else
				sen.time = 0;
		}
	}

	private static void move_to_next_grv_for_size_estimation(AgentEC agent) {
		agent.time = 0;
		agent.sum += agent.gr;
		agent.gr = 1;
		agent.done_gr = false;
	}

}

class AgentEC {
	long id, avg;
	int clk, gr, epoch, sum, time, id_length;
	boolean done_clk, done_gr, done_id, leader, init;
	char state;

	public AgentEC() {
		this.state = 'X';
		this.done_clk = this.done_gr = false;
		this.time = this.sum = this.epoch = 0;
		this.gr = this.clk = 1;
	}
}
