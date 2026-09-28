# Use Case: Pet Training

A use case elaborated from sentence 3 of `PetShop_UseCases.txt`.

**Source sentence**: *"Some pet shops offer training classes or resources to help pet owners train their pets."*

---

## 1. Metadata

| Field               | Value |
|---------------------|-------|
| **Use Case ID**     | UC-03 |
| **Use Case Name**   | Pet Training |
| **Primary Actor**   | Customer (Pet Owner) |
| **Secondary Actors** | Trainer, Pet Shop System, Training Booking System, Payment Gateway |

---

## 2. Overview

| Field         | Description |
|---------------|-------------|
| **Description** | The pet shop offers training classes and self-study resources — such as courses, guides, and videos — to help pet owners train their pets on obedience, behavior, and socialization. |
| **Trigger**   | Customer signs up for a training class, workshop, or purchases training resources for their pet. |

---

## 3. Conditions

| Condition                   | Description |
|-----------------------------|-------------|
| **Pre-Conditions**          | Pet shop has qualified trainers or curated training materials available. Customer is registered and has a pet profile, and the pet meets any class prerequisites (e.g., vaccination, age). |
| **Post-Conditions (Success)** | Customer has enrolled in the class, completed the training (or accessed the resources), and receives follow-up materials or a progress record. |
| **Post-Conditions (Failure)** | Enrollment is not completed, payment is not processed (or fully refunded), and the customer is informed of the reason (e.g., class full, pet ineligible). |

---

## 4. Main Flow (Happy Path)

1. Customer browses available training classes (e.g., basic obedience, house training, agility) and resources.
2. Customer selects a class, reviews schedule and prerequisites, and registers their pet.
3. System validates capacity and eligibility, then confirms the enrollment.
4. Customer completes payment.
5. Trainer conducts the class sessions and provides instruction and feedback.
6. System marks the class/lesson progress as complete and issues completion records or certificates.
7. Customer receives follow-up exercises and resources to continue training at home.

---

## 5. Alternate & Exception Flows

### 5.1 Alternate Flow 1: Resource-Only Training

- **At Step** 2 of Main Flow: Customer purchases self-study resources (guides, videos, webinars) instead of enrolling in a live class.
- **System Action**: System grants immediate access to the resources library and tracks consumption progress without live trainer sessions.

### 5.2 Exception Flow 1: Class Full or Prerequisite Not Met

- **At Step** 3 of Main Flow: The selected class has no openings, or the pet does not meet vaccination/age prerequisites.
- **System Action**: System informs the customer, offers the next available session or alternative classes, and does not process payment.

### 5.3 Exception Flow 2: Trainer Cancellation

- **At Step** 5 of Main Flow: A scheduled session is cancelled or rescheduled by the trainer.
- **System Action**: System notifies the customer, offers a make-up session or refund, and updates the customer's schedule.

---

## 6. Non-Functional Requirements & Business Rules

| Category              | Description |
|-----------------------|-------------|
| **Business Rules**    | Pets must meet vaccination and age requirements to join group classes. Class sizes must not exceed the trainer-to-pet ratio mandated by safety policy. Non-started classes are fully refundable. |
| **Performance / Security** | Enrollment and payment must process within 2 seconds. Customer and pet records must be stored securely. Virtual training content should stream reliably with minimal buffering. |

---

*End of Use Case UC-03*

**Created from**: `PetShop_UseCases.txt` — Sentence 3