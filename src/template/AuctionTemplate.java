package template;

//the list of imports
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import logist.Measures;
import logist.behavior.AuctionBehavior;
import logist.agent.Agent;
import logist.simulation.Vehicle;
import logist.plan.Plan;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.task.TaskSet;
import logist.topology.Topology;
import logist.topology.Topology.City;

/**
 * A very simple auction agent that assigns all tasks to its first vehicle and
 * handles them sequentially.
 * 
 */
@SuppressWarnings("unused")
public class AuctionTemplate implements AuctionBehavior {

	private final int TOTAL_ITERATIONS = 10000;

	private Topology topology;
	private TaskDistribution distribution;
	private Agent agent;
	private Random random;
	private List<Vehicle> vehicles;

    private ArrayList<Task> carriedTasks = new ArrayList<>();
    private long currentCost;

	@Override
	public void setup(Topology topology, TaskDistribution distribution,
			Agent agent) {

		this.topology = topology;
		this.distribution = distribution;
		this.agent = agent;
		this.vehicles = agent.vehicles();


		long seed = -9019554669489983951L * System.currentTimeMillis() * agent.id();
		this.random = new Random(seed);
	}

	@Override
	public void auctionResult(Task previous, int winner, Long[] bids) {

	}
	
	@Override
	public Long askPrice(Task task) {

		for (Vehicle vehicle : vehicles){
            if (vehicle.capacity() < task.weight) {
                return null;
            }
        }
        ArrayList<Task> tasks = new ArrayList<>(carriedTasks);
        tasks.add(task);
        long newCost = computeCost(tasks);

        double marginalCost = Math.abs(newCost - currentCost);

		double ratio = 1.0 + (random.nextDouble() * 0.05 * task.id);
		double bid = ratio * marginalCost;

		return (long) Math.round(bid);
	}

	@Override
	public List<Plan> plan(List<Vehicle> vehicles, TaskSet tasks) {

		long time_start = System.currentTimeMillis();

		CentralizedPlanner plans = new CentralizedPlanner(vehicles, tasks);
		int counter = 0;
		do{
			plans.chooseNeighbours();
			plans.localChoice();
			counter++;
		}while(counter < TOTAL_ITERATIONS);

		long time_end = System.currentTimeMillis();
		long duration = time_end - time_start;
		System.out.println("The plan was generated in "+duration+" milliseconds.");
		List<Plan> finalPlans = plans.getPlan();
		System.out.println(finalPlans.toString());

		return finalPlans;
	}

    private long computeCost (ArrayList<Task> tasks){
        CentralizedPlanner plans = new CentralizedPlanner(vehicles, tasks);
        int counter = 0;
        do{
            plans.chooseNeighbours();
            plans.localChoice();
            counter++;
        }while(counter < TOTAL_ITERATIONS);

        List<Plan> finalPlans = plans.getPlan();
        long cost = 0L;
        for (Vehicle vehicle : vehicles){
            cost += finalPlans.get(vehicle.id()).totalDistance()*vehicle.costPerKm();
        }
        return cost;
    }

}
