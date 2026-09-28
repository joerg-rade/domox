# Use Case: Pet Socialization

A use case elaborated from sentence 12 of `PetShop_UseCases.txt`.

**Source sentence**: *"Pet shops can organize socialization events or playgroups for pets to interact with other animals and people."*

---

## 1. Metadata

| Field               | Value |
|---------------------|-------|
| **Use Case ID**     | UC-12 |
| **Use Case Name**   | Pet Socialization |
| **Primary Actor**   | Customer (Pet Owner) |
| **Secondary Actors** | Event/Playgroup Facilitator, Pet Shop System, Booking System, Payment Gateway |

---

## 2. Overview

| Field         | Description |
|---------------|-------------|
| **Description** | The pet shop organizes socialization events and playgroups where pets can safely interact with other animals and people in a supervised setting, supporting healthy behavior development. |
| **Trigger**   | Customer registers their pet for a socialization event or playgroup session. |

---

## 3. Conditions

| Condition                   | Description |
|-----------------------------|-------------|
| **Pre-Conditions**          | An event/playgroup is scheduled with capacity and trained supervision. Pet is registered, up to date on vaccinations, and assessed as suitable for group interaction. |
| **Post-Conditions (Success)** | The pet participates safely in the session, interactions are supervised and positive, the owner receives a session summary, and fees are settled. |
| **Post-Conditions (Failure)** | The pet does not participate (or is removed for safety), the owner is notified, and any applicable refund/adjustment is applied. |

---

## 4. Main Flow (Happy Path)

1. Customer browses upcoming socialization events/playgroups (by pet type, size, age) and registers.
2. System validates capacity and eligibility, then confirms the registration.
3. Customer checks in with the pet at the event; facilitator verifies health/vaccination status.
4. Facilitator introduces the pet into the appropriately matched group.
5. Facilitator supervises interactions between pets and people, intervening as needed.
6. Facilitator logs participation notes and flags any concerns.
7. Customer picks up the pet and receives a session summary.
8. System processes payment (or package deduction) and records the attendance.

---

## 5. Alternate & Exception Flows

### 5.1 Alternate Flow 1: Introductory / Assessment Session

- **At Step** 1 of Main Flow: A first-time pet is enrolled in an assessment session before joining regular playgroups.
- **System Action**: System books the assessment, the facilitator evaluates temperament and compatibility, and recommends the appropriate group level.

### 5.2 Exception Flow 1: Pet Not Suitable for Group Interaction

- **At Step** 3 of Main Flow: The pet shows aggression, illness, or excessive stress during check-in.
- **System Action**: Facilitator declines or removes the pet from the group, notifies the owner, suggests one-on-one training or vet review, and applies the refund policy.

### 5.3 Exception Flow 2: Session Cancelled (Low Attendance / Weather)

- **At Step** 1–4 of Main Flow: The event is cancelled by the shop.
- **System Action**: System notifies registered owners, offers a credit, refund, or transfer to the next session, and updates the event status.

---

## 6. Non-Functional Requirements & Business Rules

| Category              | Description |
|-----------------------|-------------|
| **Business Rules**    | Group participation requires up-to-date vaccinations and a temperament assessment. Facilitator-to-pet ratios must meet safety policy. Aggressive or unwell pets must be excluded for group safety. |
| **Performance / Security** | Registration and check-in must process within 2 seconds. Owner/pet contact and health data are confidential and stored securely. Event capacity updates must be real time. |

---

*End of Use Case UC-12*

**Created from**: `PetShop_UseCases.txt` — Sentence 12