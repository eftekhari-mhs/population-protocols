package composition;

import java.util.Random;

public class SizeEstimation_space_opt {

	public static int population_size = 10;
	public static int cte = 281;
	public static int constant_num_grv = 5;
	public static Random rnd;
	public static int uncompleted_agents;

	public static void println(Object obj) {
		System.out.println(obj);
	}

	public static void print(Object obj) {
		System.out.print(obj);
	}

	public static void main(String[] args) {
		rnd = new Random();
		for (int jj = 0; jj < 5; jj++) {
			population_size = population_size * 10;
			println("Population Size: " + population_size);

			for (int ii = 0; ii < 10; ii++) {

				uncompleted_agents = 0;

				Agent[] agents = new Agent[population_size];
				for (int i = 0; i < agents.length; i++) {
					agents[i] = new Agent();
				}

				long number_of_interactions = 0;

				while (true) {
					number_of_interactions++;

					int rec_idx = rnd.nextInt(population_size);
					int sen_idx = rnd.nextInt(population_size);
					while (rec_idx == sen_idx)
						sen_idx = rnd.nextInt(population_size);

					Agent sen = agents[sen_idx];
					Agent rec = agents[rec_idx];

					partition_into_A_and_F(sen, rec);

					if (is_A(sen)) {
						sen.time++;
					}
					if (is_A(rec)) {
						rec.time++;
					}

					if (is_A_and_done_clk(sen)) {
						check_if_timer_done_and_increment_epoch(sen);
					}
					if (is_A_and_done_clk(rec)) {
						check_if_timer_done_and_increment_epoch(rec);
					}

					if (is_one_A_and_one_C(rec, sen)) {
						generate_clk(sen, rec);
						if (is_A_and_done_clk(rec) || is_A_and_done_clk(sen))
							generate_grv_for_size_estimation(sen, rec);
					} else if (is_A(sen) && is_S(rec)) {
						pass_gr(sen, rec);
					} else if (is_A_and_done_clk(rec) && is_A_and_done_clk(sen)) {
						propagatemax_clk(sen, rec);
						if (sen.done_gr && rec.done_gr) {
							propagatemax_grv(sen, rec);
							propagate_incremented_epoch(sen, rec);
						}
					} else if (is_S(sen) && is_S(rec)) {
						propagatemax_clk(sen, rec);
						propagatemax_epoch(sen, rec);
					}

					boolean termination = false;
					if (uncompleted_agents == 0) {
						termination = true;
						for (Agent a : agents) {
							if (is_A(a) && !a.estimation_completed)
								termination = false;
						}
					}
					if (termination)
						break;

					// if (number_of_interactions > 200 * Math.log(population_size)
					// * Math.log(population_size) * population_size/(Math.log(2)*Math.log(2)))
					// break;
				}
				int number_of_A, number_of_C, number_of_est_completed, min_estimation, max_estimation, min_clk, max_clk;
				min_clk = min_estimation = 9999;
				number_of_A = number_of_C = number_of_est_completed = max_estimation = max_clk = 0;
				for (Agent agent : agents) {
					if (agent.state == 'A')
						number_of_A++;
					if (agent.state == 'C')
						number_of_C++;
					if (agent.estimation_completed)
						number_of_est_completed++;
					if (is_S(agent)) {
						// print("generating clk status: " + (agent.done_clk) + ", ");
						// print("num grv's: " + (constant_num_grv * agent.clk) + ", ");
						// print("sum: " + (agent.sum) + ", ");
						// print("max epoch: " + (agent.epoch) + ", ");
						// println("log estimate: " + (agent.sum / (constant_num_grv * agent.clk)));
						max_estimation = Math.max(agent.sum / (agent.epoch), max_estimation);
						// min_estimation = Math.min(agent.sum / (constant_num_grv * agent.clk + 1),
						// min_estimation);
						max_clk = Math.max(agent.clk, max_clk);
						min_clk = Math.min(agent.clk, min_clk);
					}
				}

				// System.out.println("Number of agents in state A: " + number_of_A);
				// System.out.println("Number of agents in state F: " + number_of_F);
				// System.out.println("Min clk amongst the population: " + min_clk);
				System.out.println("Max clk amongst the population: " + max_clk);
				// System.out.println("Number of agents who completed the protocol: " +
				// number_of_est_completed);
				System.out.println("Actual Log of population size: " + Math.log(population_size) / Math.log(2));
				// System.out.println("Min log size estimation amongst the population: " +
				// min_estimation);
				System.out.println("Max log size estimation amongst the population: " + max_estimation);
				System.out.println("Time: " + number_of_interactions / population_size);
				System.out.println("Time/log^2 n: " + (int) (number_of_interactions / (population_size
						* (Math.log(population_size) / Math.log(2)) * (Math.log(population_size) / Math.log(2)))));
			}
		}
	}

	public static boolean is_one_A_and_one_C(Agent agent1, Agent agent2) {
		return is_A(agent1) && is_C(agent2) || is_A(agent2) && is_C(agent1);
	}

	public static boolean is_A_and_done_clk(Agent agent) {
		return is_A(agent) && agent.done_clk;
	}

	public static boolean is_A_and_done_grv_for_size_estimation(Agent agent) {
		return is_A(agent) && agent.done_gr;
	}

	public static boolean is_A(Agent agent) {
		return agent.state == 'A';
	}

	public static boolean is_C(Agent agent) {
		return agent.state == 'C';
	}

	public static boolean is_S(Agent agent) {
		return agent.state == 'S';
	}

	public static void partition_into_A_and_F(Agent sen, Agent rec) {
		/*
		 * X,X -> A,F A,X -> A,F F,X -> F,A
		 */
		if (sen.state == 'X' && rec.state == 'X') {
			sen.state = 'A';
			rec.state = 'F';
			uncompleted_agents++;
		} else if (sen.state == 'A' && rec.state == 'X') {
			rec.state = 'F';
		} else if (sen.state == 'F' && rec.state == 'X') {
			rec.state = 'A';
			uncompleted_agents++;
		}
		/*
		 * F,F -> S,C S,F -> S,C C,F -> C,S
		 */
		else if (sen.state == 'F' && rec.state == 'F') {
			sen.state = 'S';
			rec.state = 'C';
		} else if (sen.state == 'S' && rec.state == 'F') {
			rec.state = 'C';
		} else if (sen.state == 'C' && rec.state == 'F') {
			rec.state = 'S';
		}
		/*
		 * C,X -> C,A S,X -> S,A
		 */
		else if (sen.state == 'C' && rec.state == 'X') {
			rec.state = 'A';
			uncompleted_agents++;
		} else if (sen.state == 'S' && rec.state == 'X') {
			rec.state = 'A';
			uncompleted_agents++;
		}
	}

	public static void generate_clk(Agent sen, Agent rec) {
		if (sen.state == 'A' && rec.state == 'C' && !sen.done_clk) {
			sen.clk++;
		} else if (sen.state == 'C' && rec.state == 'A' && !rec.done_clk) {
			rec.done_clk = true;
		}
	}

	public static void generate_grv_for_size_estimation(Agent sen, Agent rec) {
		if (sen.state == 'A' && rec.state == 'C' && !sen.done_gr) {
			sen.gr++;
		} else if (sen.state == 'C' && rec.state == 'A' && !rec.done_gr) {
			rec.done_gr = true;
		}
	}

	public static void propagatemax_clk(Agent sen, Agent rec) {
		if (rec.clk < sen.clk) {
			rec.clk = sen.clk;
			reset_size_estimation(rec);
		} else if (sen.clk < rec.clk) {
			sen.clk = rec.clk;
			reset_size_estimation(sen);
		}
	}

	public static void propagatemax_grv(Agent sen, Agent rec) {
		if (sen.gr != rec.gr && sen.epoch == rec.epoch)
			sen.gr = rec.gr = Math.max(sen.gr, rec.gr);
	}

	public static void propagatemax_epoch(Agent sen, Agent rec) {
		sen.epoch = rec.epoch = Math.max(sen.epoch, rec.epoch);
		sen.sum = rec.sum = Math.max(sen.sum, rec.sum);
	}

	public static void reset_size_estimation(Agent agent) {
		agent.time = agent.sum = agent.epoch = 0;
		agent.gr = 1;
		if (agent.estimation_completed)
			uncompleted_agents++;
		agent.estimation_completed = agent.done_gr = agent.pass_gr = false;

	}

	/*
	 * sen <- A rec <- S
	 */
	public static void pass_gr(Agent sen, Agent rec) {
		if (sen.epoch == rec.epoch) {
			if (sen.time >= cte * sen.clk && !sen.estimation_completed) {
				rec.epoch++;
				rec.sum += sen.gr;
				sen.pass_gr = true;
			}
		} else if (sen.epoch < rec.epoch) {
			sen.pass_gr = true;
		}
	}

	public static void check_if_timer_done_and_increment_epoch(Agent agent) {
		if (agent.time >= cte * agent.clk && !agent.estimation_completed && agent.pass_gr) {
			agent.epoch++;
			move_to_next_grv_for_size_estimation(agent);
		}
		if (agent.epoch >= constant_num_grv * agent.clk && !agent.estimation_completed) {
			agent.estimation_completed = true;
			uncompleted_agents--;
		}
	}

	public static void propagate_incremented_epoch(Agent sen, Agent rec) {
		if (rec.epoch < sen.epoch) {
			rec.epoch = sen.epoch;
			move_to_next_grv_for_size_estimation(rec);
		} else if (sen.epoch < rec.epoch) {
			sen.epoch = rec.epoch;
			move_to_next_grv_for_size_estimation(sen);
		}
	}

	private static void move_to_next_grv_for_size_estimation(Agent agent) {
		agent.time = 0;
		agent.gr = 1;
		agent.done_gr = false;
		agent.pass_gr = false;
	}

}

// class Agent {
// int clk, gr, epoch, sum, time;
// boolean done_clk, done_gr, estimation_completed;
// char state;
//
// public Agent() {
// this.state = 'X';
// this.done_clk = this.done_gr = this.estimation_completed = false;
// this.time = this.sum = this.epoch = 0;
// this.gr = this.clk = 1;
// }
// }
