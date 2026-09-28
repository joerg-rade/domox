# Use Case: Pet Boarding

A use case elaborated from sentence 6 of `PetShop_UseCases.txt`.

**Source sentence**: *"Pet shops may offer boarding services for pets when their owners are away or unable to care for them temporarily."*

---

## 1. Metadata

| Field               | Value |
|---------------------|-------|
| **Use Case ID**     | UC-06 |
| **Use Case Name**   | Pet Boarding |
| **Primary Actor**   | Customer (Pet Owner) |
| **Secondary Actors** | Boarding Staff, Pet Shop System, Booking System, Payment Gateway |

---

## 2. Overview

| Field         | Description |
|---------------|-------------|
| **Description** | The pet shop provides short- or long-term boarding accommodations so customers can safely leave their pets in supervised care while they are away or otherwise unable to care for them. |
| **Trigger**   | Customer books boarding for their pet for a specific date range. |

---

## 3. Conditions

| Condition                   | Description |
|-----------------------------|-------------|
| **Pre-Conditions**          | Pet shop has boarding capacity for the requested dates. Pet has a completed health/vaccination record and passes the boarding suitability check. |
| **Post-Conditions (Success)** | The pet is cared for throughout the stay, health and feeding records are kept, the customer picks the pet up, and the boarding fee is settled. |
| **Post-Conditions (Failure)** | The pet is not boarded (or the stay is interrupted), the customer is notified, and fees are handled according to the cancellation/interruption policy. |

---

## 4. Main Flow (Happy Path)

1. Customer checks boarding availability and selects check-in/check-out dates and accommodation type.
2. System validates capacity and pet eligibility, then confirms the booking.
3. Customer drops off the pet with boarding instructions (food, medication, routine).
4. Boarding staff verify the pet's condition and record the check-in.
5. Staff provide daily care — feeding, exercise, medication, and health monitoring — and log activities.
6. System keeps the customer updated (optional photo/status updates).
7. Customer picks up the pet at check-out.
8. System processes the boarding payment and issues a receipt.

---

## 5. Alternate & Exception Flows

### 5.1 Alternate Flow 1: Extended Stay

- **At Step** 1–7 of Main Flow: The customer needs to extend the boarding duration mid-stay.
- **System Action**: System checks availability, adjusts the booking and charges, and updates the pick-up date.

### 5.2 Exception Flow 1: No Capacity or Pet Not Eligible

- **At Step** 2 of Main Flow: The requested dates are full, or the pet fails the vaccination/health check.
- **System Action**: System offers alternative dates or facilities, and no booking is confirmed until eligibility and capacity are satisfied.

### 5.3 Exception Flow 2: Pet Becomes Ill or Injured During Stay

- **At Step** 5 of Main Flow: The pet requires veterinary attention while boarding.
- **System Action**: Staff contact the owner, arrange veterinary care (per the authorized contact protocol), document the incident, and adjust billing accordingly.

### 5.4 Exception Flow 3: Early or Late Pickup

- **At Step** 7 of Main Flow: The customer picks up early, or arrives late past closing.
- **System Action**: System recalculates the prorated fee, applies any late-pickup surcharge, and updates the stay record.

---

## 6. Non-Functional Requirements & Business Rules

| Category              | Description |
|-----------------------|-------------|
| **Business Rules**    | Boarding requires up-to-date vaccinations and a health declaration. Medication administration requires written owner instructions. Deposits or cancellation fees follow the published boarding policy. |
| **Performance / Security** | Booking confirmation must complete within 2 seconds. Pet health data is confidential and must be stored securely. Facilities must meet animal-welfare and hygiene standards. |

---

*End of Use Case UC-06*

**Created from**: `PetShop_UseCases.txt` — Sentence 6