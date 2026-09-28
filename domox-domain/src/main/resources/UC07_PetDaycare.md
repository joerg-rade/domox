# Use Case: Pet Daycare

A use case elaborated from sentence 7 of `PetShop_UseCases.txt`.

**Source sentence**: *"Providing daycare services for pets can be another use case for a pet shop, allowing pet owners to leave their pets in a safe and supervised environment during the day."*

---

## 1. Metadata

| Field               | Value |
|---------------------|-------|
| **Use Case ID**     | UC-07 |
| **Use Case Name**   | Pet Daycare |
| **Primary Actor**   | Customer (Pet Owner) |
| **Secondary Actors** | Daycare Staff, Pet Shop System, Booking System, Payment Gateway |

---

## 2. Overview

| Field         | Description |
|---------------|-------------|
| **Description** | The pet shop offers daytime daycare so owners can leave their pets in a safe, supervised, and social environment (e.g., while at work), with structured play and rest. |
| **Trigger**   | Customer drops off their pet for a daycare day (recurring or one-off) at opening time. |

---

## 3. Conditions

| Condition                   | Description |
|-----------------------------|-------------|
| **Pre-Conditions**          | Pet shop has daycare slots for the day and adequate staff supervision. Pet is registered, sociable/vetted for group care, and up to date on vaccinations. |
| **Post-Conditions (Success)** | The pet has spent the day in supervised daycare with activity/rest logged, the owner picks it up, and the daily fee is settled (or charged to an active package). |
| **Post-Conditions (Failure)** | The pet is not admitted or is sent home early, the owner is notified, and no charge (or a prorated charge) applies according to policy. |

---

## 4. Main Flow (Happy Path)

1. Customer books a daycare day (or has a recurring plan) and confirms the drop-off time.
2. Customer drops off the pet; staff verify identity, health status, and any care notes.
3. Staff admit the pet to the appropriate playgroup and log the check-in.
4. Staff supervise the pet through the day — play, walks, feeding, rest — and log activities.
5. System or staff notify the owner of any notable events (optional updates/photos).
6. Customer picks up the pet at the end of the day.
7. System closes out the daycare record and processes payment or package deduction.

---

## 5. Alternate & Exception Flows

### 5.1 Alternate Flow 1: Walk-In Daycare (Same-Day Booking)

- **At Step** 1 of Main Flow: The customer arrives without a prior booking.
- **System Action**: Staff check same-day capacity and, if available, admit the pet; otherwise they advise alternatives.

### 5.2 Exception Flow 1: Pet Unwell or Aggressive on Arrival

- **At Step** 2 of Main Flow: The pet shows signs of illness or is unsuitable for group play.
- **System Action**: Staff decline admission for safety, inform the owner, and offer solo-care alternatives (if available) or no charge.

### 5.3 Exception Flow 2: Owner Late Pickup

- **At Step** 6 of Main Flow: The owner picks up the pet after daycare hours.
- **System Action**: Staff continue supervised care, the system applies the late-pickup fee, and the daycare record is updated.

---

## 6. Non-Functional Requirements & Business Rules

| Category              | Description |
|-----------------------|-------------|
| **Business Rules**    | Daycare admission requires up-to-date vaccinations and a temperament assessment. Staff-to-pet ratios must meet safety policy at all times. Pets showing illness must be isolated and the owner notified. |
| **Performance / Security** | Daycare booking and check-in/out must process quickly (within 2 seconds). Pet profiles and owner contacts are confidential and stored securely. Facility must meet hygiene and supervision standards. |

---

*End of Use Case UC-07*

**Created from**: `PetShop_UseCases.txt` — Sentence 7