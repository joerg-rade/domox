# Use Case: Veterinary Services

A use case elaborated from sentence 5 of `PetShop_UseCases.txt`.

**Source sentence**: *"Some pet shops have in-house veterinary clinics or partnerships with local veterinarians to provide medical care for pets."*

---

## 1. Metadata

| Field               | Value |
|---------------------|-------|
| **Use Case ID**     | UC-05 |
| **Use Case Name**   | Veterinary Services |
| **Primary Actor**   | Customer (Pet Owner) |
| **Secondary Actors** | Veterinarian, Veterinary Clinic System, Pet Shop System, Payment Gateway |

---

## 2. Overview

| Field         | Description |
|---------------|-------------|
| **Description** | The pet shop provides (directly or via partner veterinarians) medical care for pets, including consultations, check-ups, vaccinations, and treatment planning. |
| **Trigger**   | Customer schedules a veterinary visit or brings in a pet needing medical attention. |

---

## 3. Conditions

| Condition                   | Description |
|-----------------------------|-------------|
| **Pre-Conditions**          | Veterinarian or partner clinic is available. Customer has a pet profile (medical history) on record, and any necessary consent for treatment is obtained. |
| **Post-Conditions (Success)** | The pet receives a diagnosis and required care, a treatment/medication plan is recorded, payment (or insurance claim) is handled, and follow-up is scheduled. |
| **Post-Conditions (Failure)** | No treatment is administered, records remain unchanged, the customer is informed of the reason, and any partial stage is documented for follow-up. |

---

## 4. Main Flow (Happy Path)

1. Customer books a veterinary consultation or walks in with the pet.
2. System presents the pet's medical history to the veterinarian.
3. Veterinarian examines the pet and forms a diagnosis.
4. Veterinarian recommends a treatment plan (medication, procedure, or referral).
5. System records the diagnosis, treatment, and prescriptions in the pet's history.
6. Customer approves and pays for the services (or initiates an insurance claim).
7. System schedules any follow-up visit and provides care instructions to the customer.

---

## 5. Alternate & Exception Flows

### 5.1 Alternate Flow 1: Emergency / Walk-In Care

- **At Step** 1 of Main Flow: A pet requires urgent care without a prior booking.
- **System Action**: System triages availability, prioritizes the emergency case (surge capacity allowing), and records the walk-in visit.

### 5.2 Exception Flow 1: Referral to Specialist

- **At Step** 4 of Main Flow: The condition is beyond the clinic's scope.
- **System Action**: Veterinarian refers the pet to a specialist, the system records the referral and shares records, and the customer is briefed on next steps.

### 5.3 Exception Flow 2: Treatment Denied / Consent Withheld

- **At Step** 6 of Main Flow: The customer declines recommended treatment or withholds consent.
- **System Action**: System documents the refusal, provides alternative/least-cost options if available, and records that treatment did not proceed.

---

## 6. Non-Functional Requirements & Business Rules

| Category              | Description |
|-----------------------|-------------|
| **Business Rules**    | Veterinary care requires a licensed veterinarian and informed consent. Prescriptions must follow legal dispensing rules. Vaccination and medication records must be kept up to date from the visit. |
| **Performance / Security** | Medical records are sensitive and must be encrypted and access-controlled (health-data compliant). Records must load within 2 seconds during consultations. Billing must integrate with insurance where applicable. |

---

*End of Use Case UC-05*

**Created from**: `PetShop_UseCases.txt` — Sentence 5