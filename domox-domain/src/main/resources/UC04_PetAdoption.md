# Use Case: Pet Adoption

A use case elaborated from sentence 4 of `PetShop_UseCases.txt`.

**Source sentence**: *"Pet shops can facilitate pet adoptions by connecting potential owners with adoptable pets."*

---

## 1. Metadata

| Field               | Value |
|---------------------|-------|
| **Use Case ID**     | UC-04 |
| **Use Case Name**   | Pet Adoption |
| **Primary Actor**   | Potential Owner (Adopter) |
| **Secondary Actors** | Shelter/Rescue Partner, Pet Shop System, Adoption Coordinator, Veterinarian |

---

## 2. Overview

| Field         | Description |
|---------------|-------------|
| **Description** | The pet shop connects prospective owners with adoptable pets from shelter or rescue partners, guiding them through browsing, application, vetting, and handover. |
| **Trigger**   | A potential owner expresses interest in adopting a pet displayed in the shop or on its adoption listing. |

---

## 3. Conditions

| Condition                   | Description |
|-----------------------------|-------------|
| **Pre-Conditions**          | Pet shop has adoptable pets listed (from shelter partners). Adopter meets eligibility criteria (age, home environment, references) and any applicable fees are agreed. |
| **Post-Conditions (Success)** | The pet is formally adopted and transferred into the adopter's care, adoption paperwork is signed, and records are updated with the shelter and shop. |
| **Post-Conditions (Failure)** | No adoption is completed, the pet remains available, the application is rejected or withdrawn, and the adopter is informed of the reason. |

---

## 4. Main Flow (Happy Path)

1. Potential owner browses adoptable pets' profiles (photos, temperament, history, needs).
2. Adopter selects a pet and submits an adoption application.
3. Adoption coordinator reviews the application, checks references, and arranges a meet-and-greet.
4. Adopter meets the pet; both the adopter and the pet are matched successfully.
5. Coordinator verifies eligibility and finalizes the adoption agreement and any fees.
6. System updates the pet's status to "Adopted" and transfers ownership.
7. Adopter receives care documentation and post-adoption support resources.

---

## 5. Alternate & Exception Flows

### 5.1 Alternate Flow 1: Adoption Fostering First

- **At Step** 4 of Main Flow: Adopter opts to foster the pet before committing to full adoption.
- **System Action**: System records a "foster" status with a trial period, defers final transfer, and schedules a follow-up review.

### 5.2 Exception Flow 1: Pet Already Adopted or Not Available

- **At Step** 2 of Main Flow: The selected pet has already been adopted or taken off the list.
- **System Action**: System informs the adopter, removes the pet from active listings, and recommends similar adoptable pets.

### 5.3 Exception Flow 2: Application Rejected

- **At Step** 3 of Main Flow: The adopter fails eligibility checks or references are unsatisfactory.
- **System Action**: Coordinator notifies the adopter of the rejection, explains policy-based reasons where permitted, and no adoption is completed.

### 5.4 Exception Flow 3: Meet-and-Greet Mismatch

- **At Step** 4 of Main Flow: The pet and adopter home/lifestyle are not compatible, or the pet shows concerning behavior.
- **System Action**: Coordinator suggests alternative pets, and the current pet remains available for other applicants.

---

## 6. Non-Functional Requirements & Business Rules

| Category              | Description |
|-----------------------|-------------|
| **Business Rules**    | Adopters must be over the minimum legal age and complete a background/reference check. Adoption contracts and any fees must be finalized before handover. Pets must be vaccinated and assessed before listing. |
| **Performance / Security** | Adoption listings and applications must load within 2 seconds. Adopter personal data and application records must be stored securely (data protection compliant). |

---

*End of Use Case UC-04*

**Created from**: `PetShop_UseCases.txt` — Sentence 4