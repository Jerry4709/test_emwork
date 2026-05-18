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

//ข้อ 2

SELECT 
    d.doctor_id, 
    d.doctor_name
FROM 
    doctors d
INNER JOIN 
    doctor_shifts ds ON d.doctor_id = ds.doctor_id
    AND ds.shift_start <= '2026-03-19 10:00:00'
    AND ds.shift_end >= '2026-03-19 11:00:00'
    AND NOT (ds.break_start < '2026-03-19 11:00:00' AND ds.break_end > '2026-03-19 10:00:00')
WHERE 
    NOT EXISTS (
        SELECT 1 
        FROM appointments a 
        WHERE a.doctor_id = d.doctor_id
          AND a.status = 'confirmed'
          AND a.start_time < '2026-03-19 11:00:00'
          AND a.end_time > '2026-03-19 10:00:00'
    );

    //ข้อ 3

    //SELECT limit FROM patients WHERE id = ${patientId} SQL Injection ผู้ไม่หวังดีสามารถแทรกคำสั่ง SQL เข้าไปได้ เช่น ${patientId} = "1; DROP TABLE patients;" ซึ่งจะทำให้ข้อมูลในตาราง patients ถูกลบได้ 
    //แก้โดยใช้ Param $1 เพื่อแยก SQL
    //Race Condition (Time-of-Check to Time-of-Use) สาเหตุ: ระบบอ่านค่า (SELECT) ตรวจสอบเงื่อนไข (IF) และอัปเดตค่า (UPDATE) โดยไม่มีการป้องกันการเข้าถึงพร้อมกัน หากมี 2 เครื่อง (หรือ 2 Request) กดเบิกประกันพร้อมกันในเสี้ยววินาทีเดียวกัน
    //แก้โดยใช้ FOR UPDATE เพื่อ Lock แถวที่อ่านมาไว้จนกว่าจะทำการ Commit หรือ Rollback เสร็จสิ้น เพื่อป้องกันไม่ให้มีการอ่านหรือเขียนข้อมูลในแถวนั้นจาก Request อื่นจนกว่าจะเสร็จสิ้นกระบวนการของ Request ปัจจุบัน



   async function claimInsurance(patientId, treatmentCost) {
    const client = await db.getConnection();

    try {
        await client.query('BEGIN');

        const { rows } = await client.query(
            `SELECT limit FROM patients WHERE id = $1 FOR UPDATE`, 
            [patientId]
        );

        const p = rows[0]; 

        if (p.limit >= treatmentCost) {
            const newLimit = p.limit - treatmentCost;

            await client.query(
                `UPDATE patients SET limit = $1 WHERE id = $2`, 
                [newLimit, patientId]
            );

            await client.query('COMMIT');
            return true;
        }
        
        await client.query('ROLLBACK');
        return false;

    } catch (error) {
        await client.query('ROLLBACK');
        throw error;
    } finally {
        client.release();
    }
}

//ข้อ 4

Data Modeling — ออกแบบตาราง
-- ตาราง 1: บันทึกประวัติการแพ้ยาของผู้ป่วย
CREATE TABLE drug_allergies (
  allergy_id    SERIAL       PRIMARY KEY,
  patient_id    INT          NOT NULL REFERENCES patients(id),
  drug_code     VARCHAR(20)  NOT NULL,   -- รหัสยา (ATC code หรือ internal)
  drug_name     VARCHAR(200) NOT NULL,
  reaction_type VARCHAR(50)  NOT NULL,   -- 'anaphylaxis','rash','mild', ...
  severity      VARCHAR(10)  NOT NULL    -- 'critical','moderate','mild'
                CHECK (severity IN ('critical','moderate','mild')),
  recorded_by   INT          NOT NULL REFERENCES staff(id),
  recorded_at   TIMESTAMPTZ  DEFAULT NOW(),
  verified      BOOLEAN      DEFAULT FALSE,
  notes         TEXT,
  UNIQUE (patient_id, drug_code)  -- ยาเดิมบันทึกซ้ำไม่ได้
);

-- ตาราง 2: ใบสั่งยา
CREATE TABLE prescriptions (
  rx_id         SERIAL       PRIMARY KEY,
  patient_id    INT          NOT NULL REFERENCES patients(id),
  doctor_id     INT          NOT NULL REFERENCES doctors(doctor_id),
  drug_code     VARCHAR(20)  NOT NULL,
  dosage        VARCHAR(100) NOT NULL,
  prescribed_at TIMESTAMPTZ  DEFAULT NOW(),
  override_flag BOOLEAN      DEFAULT FALSE, -- แพทย์ override คำเตือน
  override_by   INT          REFERENCES doctors(doctor_id),
  override_note TEXT,                        -- เหตุผลที่ override (บังคับเมื่อ override)

  -- Database-level Constraint ป้องกันการสั่งยาที่แพ้
  CONSTRAINT no_allergy_prescription
    CHECK (
      override_flag = TRUE  
      OR
      NOT EXISTS (          
        SELECT 1 FROM drug_allergies da
        WHERE da.patient_id = patient_id
          AND da.drug_code   = drug_code
      )
    ),

  CONSTRAINT override_requires_note
    CHECK (override_flag = FALSE OR (override_note IS NOT NULL AND override_by IS NOT NULL))
);

Alert Workflow
Application Layer Check (ก่อน INSERT):
เมื่อแพทย์พิมพ์ชื่อยา ระบบ query real-time เทียบ drug_allergies ของผู้ป่วย
Popup Alert แบ่งตาม severity:
— แสดง modal blocking, ต้องกด confirm + เขียนเหตุผลจึงจะปิดได้
— แสดง banner สีเหลือง, ยังกรอกยาต่อได้แต่บันทึก audit log
Double Confirmation:
แพทย์ต้องพิมพ์ชื่อยาซ้ำ + บันทึกเหตุผลทางการแพทย์ก่อน override
Database Constraint:
แม้ bypass UI ได้ยังมี CHECK constraint ใน DB เป็น last line of defense
Audit Trail:
บันทึก override_by, override_note, timestamp ทุกครั้ง สำหรับตรวจสอบภายหลัง
สิทธิ์การ Override คำเตือน
Role	
แพทย์ทั่วไป	 ไม่ได้	 ได้ พร้อมบันทึกเหตุผล
แพทย์ผู้เชี่ยวชาญ / Senior	 ได้ พร้อมบันทึก	ได้
เภสัชกร	 แจ้งเตือนอย่างเดียว	 แจ้งเตือนอย่างเดียว
พยาบาล	 เรียกแพทย์	เรียกแพทย์

//ข้อ 5 
System Scalability — Lab Results

โรงพยาบาลควรใช้ระบบ PACS ร่วมกับ Cloud/Object Storage สำหรับจัดเก็บภาพ X-Ray ความละเอียดสูง และแยกเป็น 
Hot Storage สำหรับไฟล์ที่ใช้งานบ่อย และ Cold Storage สำหรับข้อมูลเก่า เพื่อรองรับการขยายตัวของข้อมูลในอนาคต

เพื่อให้แพทย์เปิดภาพผ่าน Mobile ได้ลื่นไหล ควรใช้ Image Compression เช่น JPEG2000 หรือ WebP ลดขนาดไฟล์ 
รวมถึงใช้ Progressive Loading โหลดภาพแบบค่อยเป็นค่อยไป และติดตั้ง Internal CDN/Cache Server ภายในโรงพยาบาลเพื่อลด Latency และลดภาระของ Server หลัก

Data Privacy (PDPA)

ข้อมูลผล Lab และ X-Ray เป็นข้อมูลสุขภาพที่มีความอ่อนไหว จึงต้องเข้ารหัสข้อมูลทั้งขณะจัดเก็บและขณะส่งผ่านเครือข่ายด้วย AES-256 และ HTTPS/TLS รวมถึงกำหนดสิทธิ์การเข้าถึงแบบ Role-Based Access Control (RBAC) ให้เฉพาะบุคลากรที่เกี่ยวข้องเท่านั้น

นอกจากนี้ควรใช้ Multi-Factor Authentication (MFA) สำหรับการเข้าสู่ระบบ บันทึก Audit Log ทุกการเข้าถึงข้อมูล และป้องกันการดาวน์โหลดหรือแชร์ข้อมูลโดยไม่ได้รับอนุญาต เพื่อให้สอดคล้องกับ PDPA และลดความเสี่ยงข้อมูลรั่วไหล

// ข้อ 6

คุณคือระบบแปลงคำบอกเล่าของผู้ป่วยเป็น JSON เท่านั้น
กฎที่ต้องปฏิบัติอย่างเคร่งครัด:
1. ห้ามวินิจฉัยโรค ห้ามสรุปสาเหตุ ห้ามเสนอการรักษา
2. บันทึกเฉพาะข้อมูลที่ผู้ป่วยพูดมาเท่านั้น
3. ข้อมูลที่ผู้ป่วยไม่ได้ระบุ ให้ใส่ null เสมอ ห้ามเดา
4. ตอบด้วย JSON เท่านั้น ห้ามมีข้อความนอก JSON
5. ห้ามใส่ comment หรือ explanation ใดๆ ใน output

JSON Schema ที่ต้องใช้:
{
  "chief_complaint": string,
  "duration_value": number | null,
  "duration_unit": "minutes"|"hours"|"days"|"weeks"|null,
  "associated_factors": string[],
  "symptom_quality": string | null,
  "reported_by_patient": true  
}

//ข้อ 7

