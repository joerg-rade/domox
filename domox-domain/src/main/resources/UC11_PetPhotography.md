# Use Case: Pet Photography

A use case elaborated from sentence 11 of `PetShop_UseCases.txt`.

**Source sentence**: *"Some pet shops offer pet photography services to capture memorable moments with pets."*

---

## 1. Metadata

| Field               | Value |
|---------------------|-------|
| **Use Case ID**     | UC-11 |
| **Use Case Name**   | Pet Photography |
| **Primary Actor**   | Customer (Pet Owner) |
| **Secondary Actors** | Photographer, Pet Shop System, Booking System, Payment Gateway |

---

## 2. Overview

| Field         | Description |
|---------------|-------------|
| **Description** | The pet shop offers photography sessions — studio shoots, holiday/seasonal themes, or events — so owners can capture memorable moments with their pets and purchase prints/digital photos. |
| **Trigger**   | Customer books a photography session or purchases a session package for their pet. |

---

## 3. Conditions

| Condition                   | Description |
|-----------------------------|-------------|
| **Pre-Conditions**          | Photographer and studio slot are available. Pet is presentable for the shoot (and calm/handled safely); the customer has chosen a package. |
| **Post-Conditions (Success)** | The session is completed, photos are produced and delivered to the customer, payment is settled, and the pet is returned safely. |
| **Post-Conditions (Failure)** | The session is not completed (or retake needed), no charge (or a partial/refund policy applies), and the customer is informed of the reason. |

---

## 4. Main Flow (Happy Path)

1. Customer browses photography packages (session length, themes, print options) and books a slot.
2. System confirms the booking and any session preferences.
3. Customer arrives with the pet; photographer prepares the set and props.
4. Photographer conducts the session, capturing posed and candid shots.
5. Photographer selects/edits the best photos and uploads them to a customer gallery.
6. Customer reviews the gallery and chooses photos and print products.
7. System processes payment for the chosen products.
8. Customer receives the digital photos and/or printed products (in-store or shipped).

---

## 5. Alternate & Exception Flows

### 5.1 Alternate Flow 1: On-Site Event / Seasonal Shoot

- **At Step** 1 of Main Flow: The session is part of a shop event (e.g., holiday photos with Santa).
- **System Action**: System links the booking to the event, applies event pricing/queueing, and delivers photos under the event package terms.

### 5.2 Alternate Flow 2: Retake Session

- **At Step** 4 of Main Flow: The pet was uncooperative or the photos are unsatisfactory per the guarantee.
- **System Action**: Photographer offers a free retake within the package terms; the system books the retake and carries over the original payment.

### 5.3 Exception Flow 1: Pet Too Stressed or Unsafe

- **At Step** 4 of Main Flow: The pet becomes stressed or unsafe to photograph.
- **System Action**: Session is stopped, the owner is notified, a partial credit or reschedule is offered, and no unusable photos are charged.

---

## 6. Non-Functional Requirements & Business Rules

| Category              | Description |
|-----------------------|-------------|
| **Business Rules**    | Photo rights/delivery terms must be stated in the package. Retake guarantees apply only within the package validity period. Pets must be handled safely with no coercion. |
| **Performance / Security** | Photo galleries must load and download within 2 seconds. Customer photos are personal data and must be stored securely with controlled sharing. Payment is PCI-DSS compliant. |

---

*End of Use Case UC-11*

**Created from**: `PetShop_UseCases.txt` — Sentence 11