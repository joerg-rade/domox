# Use Case: Pet Health and Wellness

A use case elaborated from sentence 10 of `PetShop_UseCases.txt`.

**Source sentence**: *"Pet shops can provide products and advice on maintaining the health and wellness of pets, including supplements, dental care, and preventive treatments."*

---

## 1. Metadata

| Field               | Value |
|---------------------|-------|
| **Use Case ID**     | UC-10 |
| **Use Case Name**   | Pet Health and Wellness |
| **Primary Actor**   | Customer (Pet Owner) |
| **Secondary Actors** | Pet Shop Staff/Advisor, Pet Shop System, Veterinarian (for referrals), Inventory System, Payment Gateway |

---

## 2. Overview

| Field         | Description |
|---------------|-------------|
| **Description** | The pet shop provides health-and-wellness products (supplements, dental care, preventive treatments) and qualified advice to help owners maintain their pets' wellbeing. |
| **Trigger**   | Customer asks for health/wellness advice or purchases related products for their pet. |

---

## 3. Conditions

| Condition                   | Description |
|-----------------------------|-------------|
| **Pre-Conditions**          | Pet shop stocks approved wellness products and has trained staff/advisors. Customer can describe the pet's needs (or brings the pet's health record). |
| **Post-Conditions (Success)** | Customer receives accurate advice and/or the appropriate products, payment is processed, and any follow-up (e.g., vet referral, restock) is arranged. |
| **Post-Conditions (Failure)** | Advice/purchase is not completed, no payment is charged, and the customer is informed of the reason (e.g., product unsuitable, requires veterinary prescription). |

---

## 4. Main Flow (Happy Path)

1. Customer browses or asks about health-and-wellness products (supplements, dental care, preventive treatments).
2. Advisor assesses the pet's needs (species, age, condition, current routine).
3. Advisor recommends suitable products and explains usage, dosage, and precautions.
4. Customer selects products and proceeds to checkout.
5. System validates the products (age/species suitability, prescription requirements).
6. Customer completes payment.
7. System updates inventory and provides care instructions with the receipt.
8. Advisor schedules a follow-up (optional) to review the pet's progress.

---

## 5. Alternate & Exception Flows

### 5.1 Alternate Flow 1: Condition Requires Veterinary Review

- **At Step** 3 of Main Flow: The pet shows symptoms that need professional diagnosis.
- **System Action**: Advisor declines self-treatment advice, refers the customer to the in-house vet or partner clinic, and records the referral.

### 5.2 Alternate Flow 2: Subscription / Auto-Restock

- **At Step** 4 of Main Flow: Customer subscribes to a recurring supply (e.g., monthly supplement).
- **System Action**: System creates a subscription with scheduled fulfillment and billing, pausable/cancellable by the customer.

### 5.3 Exception Flow 1: Prescription-Only Product

- **At Step** 5 of Main Flow: The selected item requires a veterinary prescription.
- **System Action**: System blocks the sale without a valid prescription, prompts the customer to provide one or obtain it from a vet, and otherwise offers suitable over-the-counter alternatives.

---

## 6. Non-Functional Requirements & Business Rules

| Category              | Description |
|-----------------------|-------------|
| **Business Rules**    | Prescription-only products must not be sold without a valid prescription. Advice must be species/age appropriate and given by trained staff. Product claims must not exceed regulatory allowance (no unverified medical claims). |
| **Performance / Security** | Product lookup and checkout must complete within 2 seconds. Customer health queries are confidential and must be handled under data-protection rules. Payment is PCI-DSS compliant. |

---

*End of Use Case UC-10*

**Created from**: `PetShop_UseCases.txt` — Sentence 10