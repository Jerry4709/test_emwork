import java.util.PriorityQueue;
import java.util.Comparator;

class Patient implements Comparable<Patient> {
    String id;
    int severity;
    int arrivalTime;
    boolean isTreated = false;

    public Patient(String id, int severity, int arrivalTime) {
        this.id = id;
        this.severity = severity;
        this.arrivalTime = arrivalTime;
    }

    @Override
    public int compareTo(Patient other) {
        if (this.severity != other.severity) {
            return Integer.compare(other.severity, this.severity); 
        }
        return Integer.compare(this.arrivalTime, other.arrivalTime); 
    }
}

class HospitalQueue {
    PriorityQueue<Patient> emergencyQueue = new PriorityQueue<>();
    PriorityQueue<Patient> normalQueue    = new PriorityQueue<>();
    
    PriorityQueue<Patient> waitingTimeQueue = new PriorityQueue<>(
        Comparator.comparingInt(p -> p.arrivalTime)
    );

    public void addPatient(String id, char type, int severity, int arrivalTime) {
        Patient p = new Patient(id, severity, arrivalTime);
        if (type == 'E') {
            emergencyQueue.add(p);
        } else {
            normalQueue.add(p);
            waitingTimeQueue.add(p);
        }
    }
}

public class Main {
    
    public static String getUrgentPatient(HospitalQueue queue, int currentTime) {
        
        while (!queue.waitingTimeQueue.isEmpty()) {
            Patient p = queue.waitingTimeQueue.peek();
            
            if (currentTime - p.arrivalTime > 60) {
                queue.waitingTimeQueue.poll(); 

                if (!p.isTreated) {
                    queue.emergencyQueue.add(p); 
                }
            } else {
                break; 
            }
        }

        while (!queue.emergencyQueue.isEmpty()) {
            Patient p = queue.emergencyQueue.poll();
            if (!p.isTreated) {
                p.isTreated = true;
                return p.id;
            }
        }

        while (!queue.normalQueue.isEmpty()) {
            Patient p = queue.normalQueue.poll();
            if (!p.isTreated) {
                p.isTreated = true;
                return p.id;
            }
        }

        return "ไม่มีผู้ป่วยในคิว";
    }
}