# Use Case: Pet Events and Workshops

A use case elaborated from sentence 13 of `PetShop_UseCases.txt`.

**Source sentence**: *"Hosting events and workshops on pet care, training, or specific pet-related topics can attract customers and provide educational opportunities."*

---

## 1. Metadata

| Field               | Value |
|---------------------|-------|
| **Use Case ID**     | UC-13 |
| **Use Case Name**   | Pet Events and Workshops |
| **Primary Actor**   | Customer (Pet Owner / Attendee) |
| **Secondary Actors** | Event Host/Speaker, Pet Shop System, Booking System, Payment Gateway |

---

## 2. Overview

| Field         | Description |
|---------------|-------------|
| **Description** | The pet shop hosts educational events and workshops on pet care, training, and pet-related topics, attracting customers and providing learning opportunities. |
| **Trigger**   | Customer registers to attend an event or workshop listed by the pet shop. |

---

## 3. Conditions

| Condition                   | Description |
|-----------------------------|-------------|
| **Pre-Conditions**          | An event/workshop is scheduled with a host/speaker, venue, and capacity. Customer registers (and pays, if ticketed) before the event. |
| **Post-Conditions (Success)** | The event is held, attendees participate and receive materials/recordings, and the shop gains engagement (attendee list, feedback, potential sales). |
| **Post-Conditions (Failure)** | The event is cancelled or the customer cannot attend; no charge (or refund/credit) applies and the customer is informed. |

---

## 4. Main Flow (Happy Path)

1. Shop schedules an event/workshop (topic, date, venue, capacity) and publishes it.
2. Customer browses upcoming events and registers (free or paid ticket).
3. System confirms the registration and sends event details/reminders.
4. Customer attends; host conducts the session with educational content and Q&A.
5. Attendees receive materials, handouts, or access to a recording.
6. System collects feedback and attendance records.
7. Follow-up: shop sends post-event content, offers related products/services, and updates the customer profile with interests.

---

## 5. Alternate & Exception Flows

### 5.1 Alternate Flow 1: Online / Hybrid Workshop

- **At Step** 1–4 of Main Flow: The event is delivered virtually (livestream) or as a hybrid in-person/online session.
- **System Action**: System issues a secure join link, manages online capacity separately, and provides recordings to registered attendees.

### 5.2 Alternate Flow 2: Event Fully Booked / Waitlist

- **At Step** 2 of Main Flow: The event has reached capacity.
- **System Action**: System places the customer on a waitlist and notifies them if a spot opens.

### 5.3 Exception Flow 1: Event Cancelled by Shop

- **At Step** 1–4 of Main Flow: The shop cancels or reschedules the event.
- **System Action**: System notifies all registrants, issues refunds/credits for paid tickets, and offers transfer to the rescheduled date.

### 5.4 Exception Flow 2: Attendee No-Show / Late Cancellation

- **At Step** 4 of Main Flow: A paid registrant cancels late or does not attend.
- **System Action**: Per ticket policy, a partial refund, credit, or no refund is applied; materials/recordings may still be provided.

---

## 6. Non-Functional Requirements & Business Rules

| Category              | Description |
|-----------------------|-------------|
| **Business Rules**    | Paid tickets must be refundable per the published event policy. Venue/facilitator capacity must never be exceeded. Events with pets (if applicable) follow the socialization/admission health rules. |
| **Performance / Security** | Registration and ticketing must process within 2 seconds. Attendee contact data is confidential. Online sessions must stream reliably with access control for paid content. |

---

*End of Use Case UC-13*

**Created from**: `PetShop_UseCases.txt` — Sentence 13