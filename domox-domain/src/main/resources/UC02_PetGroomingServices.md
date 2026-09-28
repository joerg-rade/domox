# Use Case: Pet Grooming Services

A use case elaborated from sentence 2 of `PetShop_UseCases.txt`.

**Source sentence**: *"Many pet shops provide grooming services such as bathing, haircuts, and nail trimming for pets."*

---

## 1. Metadata

| Field               | Value |
|---------------------|-------|
| **Use Case ID**     | UC-02 |
| **Use Case Name**   | Pet Grooming Services |
| **Primary Actor**   | Customer (Pet Owner) |
| **Secondary Actors** | Groomer, Pet Shop System, Grooming Booking System, Payment Gateway |

---

## 2. Overview

| Field         | Description |
|---------------|-------------|
| **Description** | The pet shop offers professional grooming services — including bathing, haircuts, and nail trimming — for customers to book and have their pets cared for by trained groomers. |
| **Trigger**   | Customer schedules a grooming appointment for their pet, either online or at the store. |

---

## 3. Conditions

| Condition                   | Description |
|-----------------------------|-------------|
| **Pre-Conditions**          | Pet shop has available groomers and grooming stations. Customer has a registered pet profile and is logged in or present at the store. |
| **Post-Conditions (Success)** | The pet has been groomed to the requested service, payment has been processed, the appointment is marked complete, and the customer is notified for pickup. |
| **Post-Conditions (Failure)** | The pet is not groomed, no payment is charged, the appointment is cancelled or rescheduled, and the customer is informed of the reason (e.g., pet too anxious, service unavailable). |

---

## 4. Main Flow (Happy Path)

1. Customer selects a grooming service (bathing, haircut, nail trimming, or a package) and books an appointment.
2. System confirms the appointment date, time, groomer, and service, and records the pet's requirements.
3. Customer drops off the pet at the store.
4. Groomer reviews the pet's profile and confirms the requested services.
5. Groomer performs the bathing, haircut, and/or nail trimming services.
6. Groomer updates the service record with notes and completion status.
7. Customer picks up the groomed pet.
8. System processes payment and issues a receipt.

---

## 5. Alternate & Exception Flows

### 5.1 Alternate Flow 1: Add-On or Package Service

- **At Step** 1 of Main Flow: Customer requests additional services (e.g., brushing, flea treatment, styling) on top of the standard package.
- **System Action**: System recalculates the service bundle, price, and required time, and updates the appointment before confirming.

### 5.2 Exception Flow 1: Pet Too Anxious or Aggressive to Groom

- **At Step** 5 of Main Flow: The pet becomes distressed, aggressive, or otherwise unsafe to groom.
- **System Action**: Groomer pauses or stops the session, notifies the customer, marks the service incomplete, and either reschedules or cancels with no charge.

### 5.3 Exception Flow 2: Customer Late for Pickup

- **At Step** 7 of Main Flow: The pet remains at the shop past the scheduled pickup time.
- **System Action**: System sends a reminder, the shop applies any boarding/after-hours fee, and staff continue supervised care until pickup.

---

## 6. Non-Functional Requirements & Business Rules

| Category              | Description |
|-----------------------|-------------|
| **Business Rules**    | Grooming must only be performed on pets with a completed health/vaccination declaration. Special-needs or aggressive pets must be flagged on the profile before booking. Services not completed must not be charged. |
| **Performance / Security** | Appointment booking must confirm within 2 seconds. Pet profiles and health notes must be stored securely (confidential customer data). Grooming stations must comply with hygiene and animal-safety standards. |

---

*End of Use Case UC-02*

**Created from**: `PetShop_UseCases.txt` — Sentence 2