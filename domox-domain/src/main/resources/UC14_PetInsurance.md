# Use Case: Pet Insurance

A use case elaborated from sentence 14 of `PetShop_UseCases.txt`.

**Source sentence**: *"Pet shops may partner with pet insurance providers to offer insurance plans for pet owners."*

---

## 1. Metadata

| Field               | Value |
|---------------------|-------|
| **Use Case ID**     | UC-14 |
| **Use Case Name**   | Pet Insurance |
| **Primary Actor**   | Customer (Pet Owner) |
| **Secondary Actors** | Pet Shop System, Insurance Provider, Underwriting System, Payment Gateway |

---

## 2. Overview

| Field         | Description |
|---------------|-------------|
| **Description** | The pet shop partners with insurance providers to offer pet insurance plans, helping owners enroll and manage coverage for veterinary and care costs. |
| **Trigger**   | Customer expresses interest in insuring their pet and starts the quote/enrollment process. |

---

## 3. Conditions

| Condition                   | Description |
|-----------------------------|-------------|
| **Pre-Conditions**          | Shop has an active partnership with one or more insurance providers. Customer has a pet profile, and the pet meets the provider's eligibility criteria (species, age, health). |
| **Post-Conditions (Success)** | The pet is covered under a selected plan, the first premium is paid, policy documents are issued, and the enrollment is recorded with the provider. |
| **Post-Conditions (Failure)** | Enrollment is not completed, no premium is charged, and the customer is informed of the reason (e.g., ineligible pet, quote declined, payment failed). |

---

## 4. Main Flow (Happy Path)

1. Customer requests a pet insurance quote for their pet.
2. System collects pet and owner details and forwards them to the insurance provider.
3. Provider returns plan options (coverage levels, premiums, exclusions).
4. Customer compares plans and selects one.
5. Customer confirms the enrollment and provides payment for the first premium.
6. System processes payment and finalizes the policy with the provider.
7. Customer receives the policy documents and coverage start date.
8. System records the policy for ongoing support and renewal reminders.

---

## 5. Alternate & Exception Flows

### 5.1 Alternate Flow 1: Compare Multiple Providers

- **At Step** 3 of Main Flow: Customer wants quotes from more than one partner provider.
- **System Action**: System forwards the details to the selected providers and presents comparable quotes side by side.

### 5.2 Exception Flow 1: Pet Ineligible for Coverage

- **At Step** 3 of Main Flow: The provider declines coverage (age, pre-existing condition, breed restrictions).
- **System Action**: System explains the provider's decision at a high level, suggests alternative providers/plans, and offers supplementary wellness products instead.

### 5.3 Exception Flow 2: Premium Payment Failed

- **At Step** 6 of Main Flow: Payment for the first premium is declined.
- **System Action**: System notifies the customer, holds the enrollment open for a limited time, and prompts for an alternative payment method.

### 5.4 Exception Flow 3: Claim / Renewal Support

- **At Step** 8 of Main Flow: Customer needs help with a claim or renews the policy later.
- **System Action**: Shop forwards the claim/renewal request to the provider, tracks the status, and informs the customer.

---

## 6. Non-Functional Requirements & Business Rules

| Category              | Description |
|-----------------------|-------------|
| **Business Rules**    | Quotes and underwriting decisions belong to the provider and must not be altered by the shop. Pre-existing conditions are excluded per policy terms. The shop must disclose its role (broker/partner) and any fees. |
| **Performance / Security** | Quote requests must complete within 2–5 seconds. Owner and pet data shared with providers must be transmitted securely and with consent (data-protection compliant). Payment is PCI-DSS compliant. |

---

*End of Use Case UC-14*

**Created from**: `PetShop_UseCases.txt` — Sentence 14