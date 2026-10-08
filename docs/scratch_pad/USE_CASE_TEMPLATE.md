# Use Case Template

A generic template for documenting use cases.

---

## 1. Metadata

| Field               | Value |
|---------------------|-------|
| **Use Case ID**     | UC-[Number] |
| **Use Case Name**   | [Action-oriented name, e.g., "Submit Expense Report"] |
| **Primary Actor**   | [User role initiating the action, e.g., "Employee"] |
| **Secondary Actors** | [Other systems or roles involved, e.g., "Finance Manager", "Payment Gateway"] |

---

## 2. Overview

| Field         | Description |
|---------------|-------------|
| **Description** | A brief 1-2 sentence summary of what this use case accomplishes and why. |
| **Trigger**   | The event that starts the use case, e.g., "User clicks 'Submit New Expense'". |

---

## 3. Conditions

| Condition                   | Description |
|-----------------------------|-------------|
| **Pre-Conditions**          | Prerequisites that must be true before starting, e.g., "User is logged in and has draft expenses saved." |
| **Post-Conditions (Success)** | State of the system after successful completion, e.g., "Expense report status changes to 'Pending Approval' and email notification is sent." |
| **Post-Conditions (Failure)** | State of the system if the process fails, e.g., "Draft remains unsent, and an error message is displayed." |

---

## 4. Main Flow (Happy Path)

1. Actor clicks [Action/Button].
2. System displays [Screen/Form].
3. Actor enters [Data] and clicks [Submit].
4. System validates [Data].
5. System updates [Database/Record] and returns confirmation message.

---

## 5. Alternate & Exception Flows

### 5.1 Alternate Flow 1: [Scenario Name, e.g., Save as Draft]

- **At Step** [X] of Main Flow: Actor selects "Save Draft" instead of "Submit".
- **System Action**: System saves current inputs without validation and sets status to "Draft".

### 5.2 Exception Flow 1: [Error Scenario, e.g., Invalid File Attachment]

- **At Step** [X] of Main Flow: Validation fails (e.g., file exceeds size limit).
- **System Action**: System displays error message "[Error Text]" and prompts actor to re-upload.

---

## 6. Non-Functional Requirements & Business Rules

| Category              | Description |
|-----------------------|-------------|
| **Business Rules**   | e.g., Expenses over $1,000 require secondary approval. |
| **Performance / Security** | e.g., Form submission must process within 2 seconds; data must be encrypted in transit. |

---

*End of Use Case Template*