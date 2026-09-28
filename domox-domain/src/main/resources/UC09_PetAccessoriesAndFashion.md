# Use Case: Pet Accessories and Fashion

A use case elaborated from sentence 9 of `PetShop_UseCases.txt`.

**Source sentence**: *"Pet shops can offer a variety of accessories and fashion items for pets, including collars, leashes, clothing, and costumes."*

---

## 1. Metadata

| Field               | Value |
|---------------------|-------|
| **Use Case ID**     | UC-09 |
| **Use Case Name**   | Pet Accessories and Fashion |
| **Primary Actor**   | Customer (Pet Owner) |
| **Secondary Actors** | Pet Shop System, Inventory System, Payment Gateway |

---

## 2. Overview

| Field         | Description |
|---------------|-------------|
| **Description** | The pet shop sells pet accessories and fashion items — collars, leashes, clothing, and costumes — in-store and online, allowing customers to equip and style their pets. |
| **Trigger**   | Customer browses or searches for accessories/fashion items and decides to purchase one or more. |

---

## 3. Conditions

| Condition                   | Description |
|-----------------------------|-------------|
| **Pre-Conditions**          | Accessories/fashion catalog is up to date with sizes, colors, and stock levels. Customer is present in store or browsing the online shop. |
| **Post-Conditions (Success)** | Customer purchases the selected items, payment is processed, stock is updated, and the items are handed over or shipped. |
| **Post-Conditions (Failure)** | Purchase is not completed, no payment is charged, stock is unchanged, and the customer is informed of the reason (e.g., size unavailable, payment declined). |

---

## 4. Main Flow (Happy Path)

1. Customer browses accessory and fashion categories (collars, leashes, clothing, costumes).
2. Customer filters by pet type, size, and style and selects items.
3. Customer verifies fit/sizing (in-store fitting or online size guide).
4. Customer adds items to the cart and proceeds to checkout.
5. System validates stock and availability of the selected sizes.
6. Customer completes payment (in-store register or online checkout).
7. System updates inventory and issues a receipt.
8. Customer receives the items (at the counter or via delivery).

---

## 5. Alternate & Exception Flows

### 5.1 Alternate Flow 1: Custom / Personalized Item

- **At Step** 2 of Main Flow: Customer orders a personalized item (e.g., engraved name tag, custom costume).
- **System Action**: System captures the personalization details, applies lead time and any extra fee, and notifies the customer when ready or shipped.

### 5.2 Alternate Flow 2: Gift Purchase

- **At Step** 1 of Main Flow: Customer buys items as a gift for another pet owner.
- **System Action**: System supports gift wrapping and a gift receipt option without pricing details.

### 5.3 Exception Flow 1: Size or Variant Unavailable

- **At Step** 5 of Main Flow: The requested size or color variant is out of stock.
- **System Action**: System notifies the customer, offers alternatives (other size/color) or a back-order/restock notification.

---

## 6. Non-Functional Requirements & Business Rules

| Category              | Description |
|-----------------------|-------------|
| **Business Rules**    | Products must be safe and suitable for the pet species/size (no choking-hazard items in pet sizes). Fit charts must be accurate to reduce returns. Sold items must decrement stock per size variant. |
| **Performance / Security** | Catalog browsing must load within 2 seconds. Payment must be PCI-DSS compliant. Seasonal collections should reflect stock availability in real time across store and online channels. |

---

*End of Use Case UC-09*

**Created from**: `PetShop_UseCases.txt` — Sentence 9