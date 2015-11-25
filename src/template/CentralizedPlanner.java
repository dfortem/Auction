package template;

import logist.plan.Plan;
import logist.simulation.Vehicle;
import logist.task.Task;
import logist.task.TaskSet;
import logist.topology.Topology.City;

import java.util.*;

/**
 * Centralized_Agent Created by samsara on 06/11/2015.
 */
public class CentralizedPlanner
{
    public static final int PICKUP = 0;
    public static final int DELIVERY = 1;
    public static final double PROBABILITY = 0.8;

    private static Task[] tasks;
    private static List<Vehicle> vehicles;

    private ArrayList<LinkedList<Job>> jobList;
    // V0 Job(Task, Action), Job(Task, Action)
    // V1 Job(Task, Action), ...
    // V2 ...

    private Set<ArrayList<LinkedList<Job>>> neighbours;

    private ArrayList<LinkedList<Job>> bestList;
    private double finalCost;

    /**
     * Creator Function
     *
     * @param vehicles list of vehicles to save. (static value)
     * @param tasks    list of tasks to be saved in an array. (static value)
     */
    public CentralizedPlanner(List<Vehicle> vehicles, TaskSet tasks)
    {
        this.jobList = new ArrayList<>(vehicles.size());
        CentralizedPlanner.tasks = getArray(tasks);
        CentralizedPlanner.vehicles = vehicles;
        this.neighbours = new HashSet<>();

        selectInitialSolution();

        this.bestList = new ArrayList<>();
        this.finalCost = Double.MAX_VALUE;

    }

    /**
     * Remove both pickup and delivery of a task from jobList
     *
     * @param vehicle vehicle number
     * @param task    task to be removed
     */
    private static void removeJob(ArrayList<LinkedList<Job>> jobList, int vehicle, int task)
    {
        Iterator<Job> iterator = jobList.get(vehicle).listIterator();
        while (iterator.hasNext())
        {
            Job j = iterator.next();
            if (j.getT() == task)
            {
                iterator.remove(); // Remove pickup and delivery
            }
        }
    }

    // More Useful for changing order
    private static void removeJob(LinkedList<Job> jobList, int task)
    {
        Iterator<Job> iterator = jobList.listIterator();
        while (iterator.hasNext())
        {
            Job j = iterator.next();
            if (j.getT() == task)
            {
                iterator.remove(); // Remove pickup and delivery
            }
        }
    }

    private static double computeCost(LinkedList<Job> jobs, Vehicle vehicle)
    {
        Task t;
        double distance = 0;
        City homeCity = vehicle.getCurrentCity();
        for (Job j : jobs)
        {
            t = tasks[j.getT()];
            City taskCity;
            if (j.getA() == PICKUP)
            {
                taskCity = t.pickupCity;
                distance += taskCity.distanceTo(homeCity);
            } else
            {
                taskCity = t.deliveryCity;
                distance += taskCity.distanceTo(homeCity);
            }
            homeCity = taskCity;
        }
        return distance * vehicle.costPerKm();
    }

    private Task[] getArray(TaskSet tasks)
    {
        Task[] taskArray = new Task[tasks.size()];
        for (Task task : tasks)
        {
            taskArray[task.id] = task;
        }
        return taskArray;
    }

    /**
     * Give all the tasks to the biggest vehicle. If there exist some tasks that do not fit for the vehicle, then the
     * problem is unsolvable.
     */
    public void selectInitialSolution()
    {
        // Find biggest vehicle
        int vehicleId = 0;
        int capacity = 0;
        for (Vehicle v : vehicles)
        {
            int temp;                                                          //not necessary, just use v.capacity()
            if ((temp = v.capacity()) > capacity)
            {
                vehicleId = v.id();
                capacity = temp;
            }
        }

        // Add all tasks to one vehicle
        LinkedList<Job> jobs = new LinkedList<>();
        for (int i = 0; i < tasks.length; i++)
        {
            if (tasks[i].weight < capacity)
            {
                jobs.addFirst(new Job(i, DELIVERY));
                jobs.addFirst(new Job(i, PICKUP));
            } else
            {
                throw new IllegalArgumentException("Task do not fit any vehicle");
            }
        }
        for (Vehicle vehicle : vehicles)
        {
            if (vehicle.id() == vehicleId)
            {
                jobList.add(jobs);
            } else
            {
                jobList.add(new LinkedList<>());
            }
        }
    }

    public void chooseNeighbours()
    {
        //Empty neighbour list
        neighbours.clear();
        //Get a random vehicle
        int referenceVehicleId;
        Random random = new Random();
        do
        {
            referenceVehicleId = random.nextInt(vehicles.size());
        } while (jobList.get(referenceVehicleId).isEmpty());
        List<Job> referencePlan = jobList.get(referenceVehicleId);

        //Changing vehicle operator
        if (vehicles.size() > 1) {
            for (Vehicle vehicle : vehicles) {
                if (vehicle.id() != referenceVehicleId) {
                    int newIndex = referencePlan.get(0).getT();
                    Task task = tasks[newIndex];
                    if (task.weight < vehicle.capacity()) {
                        ArrayList<LinkedList<Job>> tempJob;
                        tempJob = changingVehicle(referenceVehicleId, vehicle.id());
                        if (tempJob.get(vehicle.id()).size() <= 2 && tempJob.get(referenceVehicleId).size() <= 2) {
                            neighbours.add(tempJob);
                        } else {
                            if (tempJob.get(vehicle.id()).size() > 2) {
                                LinkedList<Job> list = changingTaskOrder(tempJob.get(vehicle.id()), vehicle.id(),
                                        vehicles.get(vehicle.id()).capacity());
                                tempJob.remove(vehicle.id());
                                tempJob.add(vehicle.id(), list);
                            }
                            if (tempJob.get(referenceVehicleId).size() > 2) {
                                LinkedList<Job> listReference = changingTaskOrder(tempJob.get(referenceVehicleId),
                                        referenceVehicleId, vehicles.get(referenceVehicleId).capacity());
                                tempJob.remove(referenceVehicleId);
                                tempJob.add(referenceVehicleId, listReference);
                            }
                            neighbours.add(tempJob);
                        }
                    }
                }
            }
        } else {
            ArrayList<LinkedList<Job>> tempJob = deepCopy(jobList);
            if (tempJob.get(0).size() > 2) {
                LinkedList<Job> list = changingTaskOrder(tempJob.get(0), 0, vehicles.get(0).capacity());
                tempJob.remove(0);
                tempJob.add(0, list);
            }
            neighbours.add(tempJob);
        }
    }

    private ArrayList<LinkedList<Job>> changingVehicle(int referenceIndex, int index)
    {
        ArrayList<LinkedList<Job>> newPlan = deepCopy(jobList);

        List<Job> referencePlan = newPlan.get(referenceIndex);

        Task task = tasks[referencePlan.get(0).getT()];
        removeJob(newPlan, referenceIndex, task.id);

        LinkedList<Job> vehiclePlan = newPlan.get(index);
        vehiclePlan.addFirst(new Job(task.id, DELIVERY));
        vehiclePlan.addFirst(new Job(task.id, PICKUP));

        return newPlan;
    }

    private LinkedList<Job> deepCopySingle(LinkedList<Job> jobs)
    {
        LinkedList<Job> temp = new LinkedList<>();
        for (Job job : jobs)
        {
            try
            {
                temp.add(job.clone());
            } catch (CloneNotSupportedException e)
            {
                System.out.println(e);
            }
        }
        return temp;
    }

    /**
     * Changing task order for one vehicle.
     *
     * @param jobs     Plan of the selected vehicle
     * @param capacity capacity of the selected vehicle
     *
     * @return The set which contains all permutation of the jobList
     */
    private LinkedList<Job> changingTaskOrder(LinkedList<Job> jobs, int vehicleID, double capacity)
    {
        HashSet<LinkedList<Job>> set = new HashSet<>();
        LinkedList<Job> newPlan;
        int index = 0;

        if (jobs.get(0).getA() == PICKUP)
        {
            int task = jobs.get(0).getT();
            newPlan = deepCopySingle(jobs);
            removeJob(newPlan, task);

            insertJob(set, newPlan, task, index, capacity);
        }
        index++;

        LinkedList<Job> temp = null;
        double minimumCost = Double.MAX_VALUE;
        for (LinkedList<Job> jb : set)
        {
            double tempCost;
            if ((tempCost = computeCost(jb, vehicles.get(vehicleID))) < minimumCost)
            {
                temp = jb;
                minimumCost = tempCost;
            }
        }
        if (temp == null) {return jobs;}
        else {return temp;}
    }

    /**
     * Insert the task back to the plan at different positions.
     *
     * @param set      Set of permutation of the plan
     * @param plan     The plan without the task to be inserted
     * @param task     the task to be inserted into the plan at different positions
     * @param index    original index of the task. (used to avoid duplication)
     * @param capacity capacity of the vehicle
     */
    private void insertJob(HashSet<LinkedList<Job>> set, LinkedList<Job> plan, int task, int index, double capacity)
    {
        int i = 0;
        double load = 0;
        double taskWeight = tasks[task].weight;

        for (Job j : plan)
        {
            if (((capacity - load) > taskWeight) && (i != index))
            {
                LinkedList<Job> newPlan = deepCopySingle(plan);
                newPlan.add(i, new Job(task, PICKUP));

                insertDelivery(set, newPlan, task, capacity);
            }

            int weight = tasks[j.getT()].weight;

            if (j.getA() == PICKUP)
            {
                load += weight; // Pickup
            } else
            {
                load -= weight; // Delivery
            }
            i++;
        }
    }

    /**
     * Insert the task back to the plan at different positions.
     *
     * @param set      Set of permutation of the plan
     * @param plan     The plan without the deliveryTask
     * @param task     the DeliveryTask to be inserted into the plan at different positions
     * @param capacity capacity of the vehicle
     */
    private void insertDelivery(HashSet<LinkedList<Job>> set, LinkedList<Job> plan, int task, double capacity)
    {
        boolean isAfterPickup = false;
        double load = 0;
        int index = 0;

        for (Job j : plan)
        {
            if (j.getT() == task)
            {
                isAfterPickup = true;
            }

            // current payload
            int weight = tasks[j.getT()].weight;

            if (j.getA() == PICKUP)
            {
                load += weight; // Pickup
            } else
            {
                load -= weight; // Delivery
            }

            if (load > capacity)
            {
                return;
            }

            if (isAfterPickup)
            {
                LinkedList<Job> newPlan = deepCopySingle(plan);
                newPlan.add(index + 1, new Job(task, DELIVERY));
                set.add(newPlan);
            }
            index++;
        }
    }


    private ArrayList<LinkedList<Job>> deepCopy(ArrayList<LinkedList<Job>> initialList)
    {
        ArrayList<LinkedList<Job>> newList = new ArrayList<>();
        for (LinkedList<Job> list : initialList)
        {
            LinkedList<Job> temp = new LinkedList<>();
            for (Job job : list)
            {
                try
                {
                    temp.add(job.clone());
                } catch (CloneNotSupportedException e)
                {
                    System.out.println(e);
                }
            }
            newList.add(temp);
        }
        return newList;
    }

    public List<Plan> getPlan()
    {
        List<Plan> finalList = new ArrayList<>();
        int vehicleID = 0;
        for (LinkedList<Job> plan : bestList)
        {
            //Initialize plan
            City current = vehicles.get(vehicleID).getCurrentCity();
            Plan completePlan = new Plan(current);
            //create correct Plan
            if (plan != null)
            {
                for (Job action : plan)
                {
                    //Get task from action
                    Task currentTask = tasks[action.getT()];
                    //find route to action city
                    if (action.getA() == PICKUP)
                    {
                        for (City city : current.pathTo(currentTask.pickupCity))
                        {
                            completePlan.appendMove(city);
                        }
                        completePlan.appendPickup(currentTask);
                        current = currentTask.pickupCity;
                    } else
                    {
                        for (City city : current.pathTo(currentTask.deliveryCity))
                        {
                            completePlan.appendMove(city);
                        }
                        completePlan.appendDelivery(currentTask);
                        current = currentTask.deliveryCity;
                    }
                }
            }
            finalList.add(completePlan);
            vehicleID++;
        }
        while (finalList.size() < vehicles.size())
        {
            finalList.add(Plan.EMPTY);
        }
        return finalList;
    }

    public void localChoice()
    {
        ArrayList<ArrayList<LinkedList<Job>>> bestSolutions = new ArrayList<>();
        double minCost = Double.MAX_VALUE;
        for (ArrayList<LinkedList<Job>> list : neighbours)
        {
            double tempCost = 0;
            int vehicleID = 0;
            for (LinkedList<Job> vehicleJob : list)
            {
                tempCost += computeCost(vehicleJob, vehicles.get(vehicleID));
                vehicleID++;
            }
            if (tempCost <= minCost)
            {
                if (tempCost != minCost)
                {
                    bestSolutions.clear();
                }
                bestSolutions.add(list);
                minCost=tempCost;
            }
        }
        if (bestSolutions.isEmpty())
        {
            System.out.println("Didn't Find any neighbor solution!");
            return;
        }
        Random random = new Random();
        int chosenSolution = random.nextInt(bestSolutions.size());
        ArrayList<LinkedList<Job>> bestSolution = bestSolutions.get(chosenSolution);

        //Save best solution
        if (minCost < finalCost)
        {
            bestList = bestSolution;
            finalCost = minCost;
        }

        int cost = 0;
        int vehicleID = 0;
        for (LinkedList<Job> vehicleJob : bestSolution)
        {
            cost += computeCost(vehicleJob, vehicles.get(vehicleID));
            vehicleID++;
        }
        double probability = random.nextDouble();

        double tempCost1 = 0;
        int vehicleID2 = 0;
        for (LinkedList<Job> vehicleJob : jobList)
        {
            tempCost1 += computeCost(vehicleJob, vehicles.get(vehicleID2));
            vehicleID2++;
        }

        if (tempCost1 > minCost)
        {
            if (probability < PROBABILITY)
            {
                jobList = bestSolution;
            }

        } else
        {
            if (probability > PROBABILITY)
            {
                jobList = bestSolution;
            }
        }
    }

    @Override
    public String toString(){
        String string = new String();
        for (LinkedList<Job> list: jobList){
            string += list.toString() + "\n";
        }
        return string;
    }

    private class Job implements Cloneable
    {
        private final int t; // index of the task in tasks array
        private final int a; // PICKUP or DELIVERY

        public Job(int t, int a)
        {
            this.t = t;
            this.a = a;
        }

        public Job(Job j)
        {
            this.t = j.getT();
            this.a = j.getA();
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Job job = (Job) o;

            if (t != job.t) return false;
            return a == job.a;

        }

        @Override
        public int hashCode()
        {
            int result = t;
            result = 31 * result + a;
            return result;
        }

        public int getT()
        {
            return t;
        }

        public int getA()
        {
            return a;
        }

        @Override
        public String toString()
        {
            if (a == PICKUP)
            {
                return ("PICKUP TASK " + t);
            } else
            {
                return ("DELIVER TASK " + t);
            }
        }

        @Override
        protected Job clone() throws CloneNotSupportedException
        {
            return (Job) super.clone();
        }
    }
}
