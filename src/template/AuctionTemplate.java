package template;

//the list of imports
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import com.sun.tools.doclets.formats.html.SourceToHTMLConverter;
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

    private final double epsilon = 0.1;
    private long currentCost;
    private double ratio = 1;
    private ArrayList<Task> carriedTasks = new ArrayList<>();

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
        long tempCost = currentCost;
        if (winner == agent.id()){
            carriedTasks.add(previous);
            currentCost = computeCost(carriedTasks);
        }
        if (bids.length > 1) {
            long difference = Math.abs(bids[0] - bids[1]);
            long betterBid;
            if (winner == agent.id()){
                betterBid = bids[agent.id()] + difference/2;
            } else {
                betterBid = bids[agent.id()] - difference/2;
            }
            if (bids[agent.id()] != 0) {
                ratio = betterBid / bids[agent.id()] * ratio;
            }
            if (ratio < 1) {
                ratio = 1;
            }
        }
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

        if (marginalCost < epsilon){
            double costPerKM = Double.MAX_VALUE;
            for (Vehicle vehicle : vehicles){
                if (costPerKM > vehicle.costPerKm()){
                    costPerKM = vehicle.costPerKm();
                }
            }
            marginalCost = (long) task.pickupCity.distanceTo(task.deliveryCity)/2*costPerKM;
        }
        if (carriedTasks.size() < 1) {
            ratio = 0.75;
        } else if (carriedTasks.size() < 2) {
            ratio = 0.95;
        }

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
