# Use Case: Pet-Themed Merchandise

A use case elaborated from sentence 15 of `PetShop_UseCases.txt`.

**Source sentence**: *"Pet shops can sell pet-themed merchandise, such as phone cases, clothing, and accessories, to pet lovers."*

---

## 1. Metadata

| Field               | Value |
|---------------------|-------|
| **Use Case ID**     | UC-15 |
| **Use Case Name**   | Pet-Themed Merchandise |
| **Primary Actor**   | Customer (Pet Lover) |
| **Secondary Actors** | Pet Shop System, Inventory System, Payment Gateway |

---

## 2. Overview

| Field         | Description |
|---------------|-------------|
| **Description** | The pet shop sells pet-themed merchandise — phone cases, clothing, and accessories — targeting pet lovers as a lifestyle/apparel offering alongside pet products. |
| **Trigger**   | Customer browses or searches pet-themed merchandise and decides to purchase one or more items. |

---

## 3. Conditions

| Condition                   | Description |
|-----------------------------|-------------|
| **Pre-Conditions**          | Merchandise catalog is stocked with designs, sizes, and quantities available. Customer is in store or logged into the online shop. |
| **Post-Conditions (Success)** | Customer purchases the merchandise, payment is processed, stock is updated, and items are handed over or shipped. |
| **Post-Conditions (Failure)** | Purchase is not completed, no payment is charged, stock is unchanged, and the customer is informed of the reason (e.g., item/size unavailable, payment declined). |

---

## 4. Main Flow (Happy Path)

1. Customer browses pet-themed merchandise (phone cases, clothing, accessories).
2. Customer filters by product type, design, and size, then selects items.
3. Customer adds items to the cart and proceeds to checkout.
4. System validates stock and availability.
5. Customer completes payment (register or online checkout).
6. System updates inventory and issues a receipt.
7. Customer receives the items (at the counter or via delivery).

---

## 5. Alternate & Exception Flows

### 5.1 Alternate Flow 1: Exclusive / Limited-Edition Item

- **At Step** 2 of Main Flow: Customer is interested in an exclusive or limited-edition design.
- **System Action**: System shows remaining stock, applies any per-customer purchase limits, and notifies when nearly sold out.

### 5.2 Alternate Flow 2: Bulk / Wholesale Purchase

- **At Step** 3 of Main Flow: A customer (e.g., business) orders merchandise in bulk.
- **System Action**: System applies tiered pricing and handles the bulk order through a separate fulfillment flow with delivery scheduling.

### 5.3 Exception Flow 1: Item or Size Out of Stock

- **At Step** 4 of Main Flow: The requested design or size is unavailable.
- **System Action**: System offers alternatives, a restock notification, or a back-order where supported.

---

## 6. Non-Functional Requirements & Business Rules

| Category              | Description |
|-----------------------|-------------|
| **Business Rules**    | Merchandise is for human use and must meet applicable product-safety standards (labels, materials). Limited editions must honor published quantity limits. Sold items decrement stock per SKU. |
| **Performance / Security** | Catalog and checkout must load within 2 seconds. Payment is PCI-DSS compliant. Stock availability must sync in real time between store and online channels. |

---

*End of Use Case UC-15*

**Created from**: `PetShop_UseCases.txt` — Sentence 15