# Canadian Mileage Tracking App PRD

## Overview
This document outlines a product requirements framework for a Canadian mileage and vehicle-expense app designed to compete with MileIQ. The core opportunity is to combine automatic mileage capture with Canada-specific compliance, reimbursement, payroll, and bookkeeping workflows that generic mileage apps only partially address.

The product should be positioned as a Canada-first mileage platform for self-employed professionals, incorporated business owners, and small-to-midsize employers that need defensible records for CRA reporting and practical workflows for employee reimbursement.

## Product goals
The product should achieve four goals:

- Deliver highly reliable automatic trip capture with a low-friction mobile experience comparable to leading mileage trackers.
- Produce CRA-ready records that support deductions, reimbursements, and audit defense rather than generic trip logs alone.
- Support both solo users and managed teams with policy, approval, and payroll workflows suited to Canadian businesses.
- Differentiate from MileIQ by treating kilometres, Canadian tax context, and bilingual/local expectations as core product assumptions instead of optional settings.

## Target users
### Primary segments

1. Self-employed professionals and sole proprietors who need accurate annual mileage logs and deductible vehicle-expense documentation for CRA filings.
2. Small business employees who drive for work and need mileage reimbursement under company policy.
3. Finance and operations admins who need review, approval, export, and payroll support for multiple drivers.
4. Incorporated owner-operators and field teams with more than one vehicle, changing reimbursement rates, or mixed personal/business usage.

### Secondary segments

- Accountants and bookkeepers supporting clients with vehicle-expense claims.
- Sales, construction, home services, healthcare, and nonprofit field staff who log frequent local travel.
- Employers seeking a lightweight alternative to broader fleet or expense-management suites.

## Core product principles
- Kilometres first, not miles first with Canadian settings layered on top.
- Compliance by default, with records structured around CRA expectations for motor vehicle expenses and allowances.
- Reliability over novelty, because missed trips and poor audit trails destroy trust in this category.
- Dual-mode product design: simple enough for solo users, structured enough for payroll and admin teams.
- Clear value proof through saved admin time, reimbursement accuracy, and stronger tax documentation.

## Functional requirements
### Mobile tracking engine
The MVP mobile app should include:

- Automatic trip detection using device motion and location services.
- Start/stop trip capture fallback for users who prefer explicit control.
- Manual trip entry and trip repair for missed drives.
- Route visualization on a map.
- Trip classification as business, personal, commute, medical, charity, or custom categories.
- Frequent-trip suggestions and rule-based auto-classification.
- Odometer capture by photo and manual entry.
- Offline trip buffering with delayed sync.
- Duplicate-trip detection and merge resolution.
- Battery optimization guidance and diagnostics.

These capabilities are required because the category baseline already includes passive drive tracking, trip categorization, and report generation, while user trust depends heavily on the app not missing trips during real-world usage.

### Trip data model
Each trip record should support:

- Date and time.
- Start and end location.
- Purpose of trip.
- Distance in kilometres.
- Route geometry and confidence score.
- Vehicle used.
- Driver identity.
- Classification status.
- Editable notes and receipt attachments.
- Audit history showing edits, timestamps, and actor.

This structure aligns the app with CRA-oriented recordkeeping needs while also enabling employer review, internal controls, and defensible corrections.

### Vehicle management
The app should support:

- Multiple vehicles per user.
- Active/inactive vehicles.
- Vehicle type and ownership model.
- Fiscal-year odometer opening and closing values.
- Mid-year vehicle change workflows.
- Vehicle-level summaries and reports.

CRA guidance expects records tied to specific vehicles, including odometer details for the beginning and end of the fiscal period and treatment of vehicle changes during the year.

## Canadian compliance requirements
### CRA logbook support
The product should include a dedicated CRA logbook mode that captures:

- Date of each business trip.
- Destination.
- Purpose.
- Number of kilometres driven.
- Beginning and ending odometer readings for the year.
- Vehicle-specific annual totals.

Canada Revenue Agency guidance for motor vehicle expenses emphasizes maintaining a logbook with business-trip details and annual odometer records, which should be reflected directly in the user workflow and report outputs.

### Simplified logbook support
The app should offer a simplified-logbook workflow for users who qualify to maintain a full base-year logbook and use a reduced sample in later years, with guidance screens explaining when a user can and cannot rely on that method.

### Record retention
The system should preserve report history and exportable supporting records for at least six years, because CRA recordkeeping expectations extend to retention of supporting documents and logs used in claims.

### Allowance and reimbursement logic
The app should support:

- CRA automobile allowance reference rates by year.
- Custom company reimbursement rates.
- Per-kilometre reimbursement calculations.
- Taxable vs non-taxable allowance guidance fields for employers.
- Territory-aware and policy-aware rate overrides.

CRA guidance distinguishes between reasonable and non-reasonable allowances and explains how reimbursements and allowances are treated for employees, which makes policy configuration essential for employer-facing workflows.

## Expense and bookkeeping requirements
A strong Canadian competitor should not stop at mileage. It should also support vehicle-expense documentation that helps users defend broader claims and complete bookkeeping workflows.

Required capabilities:

- Fuel, maintenance, insurance, parking, tolls, lease, loan-interest, and registration expense tracking.
- Receipt capture and OCR-assisted metadata extraction.
- Allocation of expenses by vehicle.
- Business-use percentage calculations using logbook totals.
- Export packages for accountants and bookkeepers.
- Fiscal-year summaries aligned to Canadian tax filing periods.
- GST/HST-aware tagging and export fields where relevant to business bookkeeping.

CRA motor vehicle expense guidance discusses categories such as fuel, insurance, maintenance, licence and registration, leasing costs, and interest, so expanding beyond mileage is a meaningful way to increase product value in Canada.

## Team and admin requirements
### Admin console
The admin experience should include:

- Driver invitations and role-based permissions.
- Team, department, and cost-centre assignment.
- Policy templates for mileage reimbursement.
- Approval queues for submitted trips and reports.
- Exception flags for suspicious, duplicate, or incomplete logs.
- Bulk export for payroll and accounting.
- Web dashboard for month-end review.
- Deactivation, archival, and audit access controls.

Many alternatives in this category differentiate on team operations, not just logging, and this is the area where a new entrant can create stronger B2B value than a solo-first tracker.

### Manager workflows
Managers should be able to:

- Review submitted logs.
- Approve, reject, or return items.
- Request missing purpose or attachment details.
- Enforce commute and personal-use policies.
- Lock approved periods.
- View reimbursement liability before payroll run.

These workflows directly address employer reimbursement administration and improve defensibility for kilometre-based allowances.

## Reporting requirements
The product should generate:

- Monthly, quarterly, and annual mileage summaries.
- Per-vehicle and per-driver reports.
- CRA-ready logbook exports.
- Reimbursement statements.
- Payroll export summaries.
- Accountant handoff packages.
- CSV and PDF exports.
- Exception and audit-history reports.

MileIQ already competes on report generation, so a Canadian competitor must exceed that baseline with outputs explicitly tailored to CRA review, Canadian payroll processes, and accountant workflows.

## Localization requirements
To feel native in the Canadian market, the product should include:

- English and French interfaces.
- Kilometres and litres as defaults.
- Canadian currency formatting.
- Province and territory-aware business settings.
- Canadian address and postal-code formatting.
- Support copy that references CRA terminology rather than IRS terminology.
- Canadian privacy, consent, and data-hosting disclosures.

MileIQ already supports a Canadian rate and distance-unit setting, so a true Canada-first product must go much further on localization and policy language than unit conversion alone.

## Integrations
### MVP integrations
- CSV export.
- Email share.
- Calendar import for trip verification.
- Map provider integration.
- Receipt-scanning service.

### V2 integrations
- QuickBooks Online.
- Xero.
- Wagepoint or comparable Canadian payroll systems.
- Microsoft 365 and Google Workspace SSO.
- Corporate card and expense platforms.

Integration depth is important because several alternatives compete as broader expense or operations platforms rather than pure mileage trackers.

## Security and trust requirements
The app should include:

- Strong audit logs for all edits.
- Admin visibility into policy overrides.
- Secure export controls.
- Consent-based location tracking.
- Clear battery and background-permission education.
- Data deletion and retention controls.
- Fraud and anomaly flags for impossible routes or duplicate submissions.

Trust is central in mileage tracking because users rely on the record for tax and payroll outcomes, and review sources show that tracking reliability issues can materially shape user sentiment.

## MVP scope
The MVP should focus on a narrow but defensible wedge:

- Automatic kilometre tracking.
- Manual correction tools.
- CRA logbook mode.
- Multi-vehicle support.
- Rate calculation for Canadian reimbursement.
- Monthly and annual exportable reports.
- English/French UI foundation.
- Receipt attachments.
- Simple admin review workflow for small teams.

This scope is sufficient to compete with solo mileage apps while establishing a differentiated Canadian compliance story.

## V2 scope
V2 should expand into workflow and bookkeeping depth:

- Full vehicle-expense management.
- Simplified-logbook automation.
- Advanced team policies and approvals.
- Payroll integrations.
- Accounting integrations.
- OCR and AI-assisted trip classification.
- Reimbursement forecasting and budgeting.
- Manager dashboards and analytics.
- Territory-specific rate templates.

This release would move the product from a mileage logger into a reimbursement and compliance platform for Canadian SMBs.

## Future scope
Future releases could include:

- Embedded accountant portal.
- Benchmarking for reimbursement cost per employee or team.
- Insurance or fleet partnerships.
- Driver coaching and route-efficiency insights.
- White-label employer portals.
- API access for fintech and payroll partners.

These features are not required for initial market entry but could expand the product into a broader vehicle-expense operating layer.

## Competitive positioning
### Core position
The strongest positioning statement is: a mileage and vehicle-expense app built specifically for Canadian tax, reimbursement, and payroll workflows.

### Why this can win
- MileIQ already has Canadian unit and rate support, but that is a settings-level adaptation rather than a full Canada-first workflow story.
- CRA record structure creates a clear product-design opportunity around audit-ready exports and vehicle-specific logs.
- Team reimbursement and payroll controls remain a meaningful point of differentiation in the mileage-tracking category.
- Canadian SMBs and self-employed users often need both mileage and expense support, not just a trip counter.

## Recommended success metrics
The product team should track:

- Trip capture success rate.
- Percentage of trips auto-classified correctly.
- Time to monthly reimbursement submission.
- Percentage of users generating year-end reports.
- Team approval turnaround time.
- Monthly retention by user segment.
- Paid conversion from solo to team plans.
- Export usage by report type.
- Support tickets related to missing trips or compliance confusion.

These metrics align with the category's trust, workflow, and retention drivers.

## Go-to-market implications
The initial go-to-market should focus on two messages: CRA-ready mileage logs for self-employed Canadians and easier reimbursement workflows for small Canadian businesses. A practical channel mix would likely include accountant partnerships, payroll and bookkeeping integrations, and vertical targeting toward field-service businesses where regular local driving creates an immediate ROI case.
