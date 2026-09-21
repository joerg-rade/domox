# Use Case: Selling Pet Products

A use case elaborated from sentence 1 of `PetShop_UseCases.txt`.

**Source sentence**: *"A pet shop can offer a wide range of pet products, including food, toys, accessories, and grooming supplies."*

---

## 1. Metadata

| Field               | Value |
|---------------------|-------|
| **Use Case ID**     | UC-01 |
| **Use Case Name**   | Selling Pet Products |
| **Primary Actor**   | Customer (Pet Owner) |
| **Secondary Actors** | Pet Shop System, Inventory System, Payment Gateway |

---

## 2. Overview

| Field         | Description |
|---------------|-------------|
| **Description** | The pet shop offers a wide range of pet products — including food, toys, accessories, and grooming supplies — allowing customers to browse and purchase items for their pets in-store or online. |
| **Trigger**   | Customer arrives at the pet shop (physical or online store) with the intent to purchase pet products. |

---

## 3. Conditions

| Condition                   | Description |
|-----------------------------|-------------|
| **Pre-Conditions**          | Pet shop has an up-to-date inventory of products across all categories (food, toys, accessories, grooming supplies). Customer is present at the store or logged into the online shop. |
| **Post-Conditions (Success)** | Customer has purchased the desired pet products, payment has been processed, inventory quantities are updated, and a receipt is provided. |
| **Post-Conditions (Failure)** | Transaction is cancelled, no payment is processed, inventory remains unchanged, and the customer is informed of the reason (e.g., item unavailable, payment declined). |

---

## 4. Main Flow (Happy Path)

1. Customer browses the available pet product categories (food, toys, accessories, grooming supplies).
2. Customer selects one or more products and adds them to their cart.
3. Customer proceeds to checkout / payment.
4. System validates product availability and calculates the total cost.
5. Customer provides payment information.
6. System processes payment and confirms the transaction.
7. System updates inventory quantities.
8. Customer receives a receipt (printed or digital).

---

## 5. Alternate & Exception Flows

### 5.1 Alternate Flow 1: Product Out of Stock

- **At Step** 2 of Main Flow: A selected product is out of stock.
- **System Action**: System notifies the customer that the item is unavailable and suggests alternatives or offers to notify when restocked.

### 5.2 Alternate Flow 2: In-Store Purchase with Assistance

- **At Step** 1 of Main Flow: Customer asks a shop assistant for help instead of self-browsing.
- **System Action**: Shop assistant retrieves requested items from stock, presents options, and processes the sale at the register.

### 5.3 Exception Flow 1: Payment Declined

- **At Step** 5–6 of Main Flow: Payment is declined (insufficient funds, expired card, etc.).
- **System Action**: System displays an error message and prompts the customer to provide an alternative payment method.

---

## 6. Non-Functional Requirements & Business Rules

| Category              | Description |
|-----------------------|-------------|
| **Business Rules**    | Product must be in stock and not expired (for food/supplies) before it can be sold. Age-restricted items (e.g., certain medications) require customer age verification. |
| **Performance / Security** | Product catalog must load within 2 seconds. Payment transactions must be encrypted (PCI-DSS compliant). Inventory updates must be reflected in real time across all channels (in-store POS and online). |

---

*End of Use Case UC-01*

**Created from**: `PetShop_UseCases.txt` — Sentence 1