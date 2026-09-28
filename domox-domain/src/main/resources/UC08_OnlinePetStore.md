# Use Case: Online Pet Store

A use case elaborated from sentence 8 of `PetShop_UseCases.txt`.

**Source sentence**: *"Operating an online pet store allows customers to conveniently purchase pet products and have them delivered to their doorstep."*

---

## 1. Metadata

| Field               | Value |
|---------------------|-------|
| **Use Case ID**     | UC-08 |
| **Use Case Name**   | Online Pet Store |
| **Primary Actor**   | Customer (Pet Owner) |
| **Secondary Actors** | Online Shop System, Inventory System, Payment Gateway, Delivery/Logistics Provider |

---

## 2. Overview

| Field         | Description |
|---------------|-------------|
| **Description** | The pet shop operates an online store where customers can browse, order, pay for, and receive pet products delivered to their doorstep. |
| **Trigger**   | Customer visits the online store, selects products, and places an order for home delivery. |

---

## 3. Conditions

| Condition                   | Description |
|-----------------------------|-------------|
| **Pre-Conditions**          | Online store is available and the customer is logged in (or can check out as guest). Items are in stock and deliverable to the customer's address. |
| **Post-Conditions (Success)** | Order is placed and paid for, items are picked and shipped, the customer receives the delivery, and inventory and order statuses are updated. |
| **Post-Conditions (Failure)** | Order is not placed or is cancelled, no payment is charged (or it is refunded), and the customer is informed of the reason (e.g., out of stock, payment declined, delivery failed). |

---

## 4. Main Flow (Happy Path)

1. Customer browses the online catalog and adds products to the cart.
2. Customer reviews the cart and proceeds to checkout.
3. System validates stock availability and calculates totals (items, tax, shipping).
4. Customer enters or confirms the delivery address and selects a delivery option.
5. Customer provides payment information.
6. System processes the payment and confirms the order.
7. System routes the order to fulfillment; staff/warehouse pick and pack the items.
8. Delivery provider ships the order to the customer's doorstep.
9. Customer receives the package; system updates order status to "Delivered" and decrements inventory.

---

## 5. Alternate & Exception Flows

### 5.1 Alternate Flow 1: Guest Checkout

- **At Step** 4 of Main Flow: Customer checks out without creating an account.
- **System Action**: System captures delivery and contact details for the single order and provides a guest order-tracking link.

### 5.2 Alternate Flow 2: Click-and-Collect (Store Pickup)

- **At Step** 4 of Main Flow: Customer chooses in-store pickup instead of home delivery.
- **System Action**: System selects a pickup store and timeslot; the customer is notified when the order is ready for collection.

### 5.3 Exception Flow 1: Item Out of Stock After Ordering

- **At Step** 7 of Main Flow: A reserved item is unavailable during fulfillment.
- **System Action**: System notifies the customer, offers a substitute or refund for that line, and ships the remaining items.

### 5.4 Exception Flow 2: Delivery Failure

- **At Step** 8 of Main Flow: The delivery cannot be completed (wrong address, no recipient, etc.).
- **System Action**: System/partner contacts the customer, attempts redelivery or a pickup alternative, and escalates to refund if unresolved.

### 5.5 Exception Flow 3: Payment Declined

- **At Step** 6 of Main Flow: Payment is declined.
- **System Action**: System informs the customer and prompts for an alternative payment method; the order remains unconfirmed until payment succeeds.

---

## 6. Non-Functional Requirements & Business Rules

| Category              | Description |
|-----------------------|-------------|
| **Business Rules**    | Orders are only confirmed after successful payment. Age-restricted products (e.g., certain medications) require age verification at delivery/checkout. Live animals are excluded from standard home delivery. |
| **Performance / Security** | Store pages and checkout must load within 2 seconds. Payment data must be PCI-DSS compliant and encrypted. Delivery tracking must update in near real time. |

---

*End of Use Case UC-08*

**Created from**: `PetShop_UseCases.txt` — Sentence 8