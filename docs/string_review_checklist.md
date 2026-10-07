# String Review Checklist — CRA Terminology

This document lists **banned terms** and their **correct CRA-aligned replacements**
for use during PR reviews of any string resource, Compose UI copy, PDF/CSV
export content, or code comment that will be user-visible.

> **Purpose:** Ensure MileageTracker reads like a Canadian-first product and
> never uses IRS, imperial, or US-tax terminology.

---

## Banned Terms and Replacements

| ❌ Banned / US Term | ✅ CRA-Aligned Replacement | Notes |
|---|---|---|
| IRS | CRA / Agence du revenu du Canada (FR) | Never reference the US tax authority |
| mileage | kilometres / kilométrage (FR) | "Mileage" implies miles; use km always |
| miles | kilometres | All distances must use metric |
| gas mileage | fuel consumption (L/100 km) | CRA uses litres per 100 km if at all |
| mileage rate | kilometric rate / taux kilométrique (FR) | CRA prescribed term |
| mileage log | vehicle logbook / registre de véhicule (FR) | CRA term per T4002 |
| mileage reimbursement | kilometric reimbursement | |
| deductible miles | eligible kilometres / km admissibles (FR) | |
| business miles | business kilometres / km d'affaires (FR) | |
| tax deduction | motor vehicle expense deduction | CRA form T2125 language |
| Schedule C | T2125 / T777 | Canadian tax form references |
| Form 1099 | T4 / T4A | |
| 1040 | T1 | |
| reimbursement rate | kilometric rate | |
| odometer mileage | odometer reading (km) | |
| zip code | postal code / code postal (FR) | |
| state | province / territory | |
| gas station | gas station (acceptable) or service station | Minor, but avoid "gas mileage" |
| MPG / miles per gallon | L/100 km | |

---

## CRA-Specific Copy Patterns

Use these exact phrases and their French equivalents for consistency:

| Context | English Copy | French Copy |
|---|---|---|
| Export file / logbook | CRA-ready vehicle logbook | Registre de véhicule aux fins de l'ARC |
| Logbook eligibility label | CRA-eligible | Admissible à l'ARC |
| Business use metric | Business use % | % d'utilisation aux fins d'affaires |
| Rate mode setting | CRA Standard (two-tier) | Taux ARC standard (deux paliers) |
| Rate description | CRA prescribed kilometric rates | Taux kilométriques prescrits par l'ARC |
| Odometer CRA note | CRA requires opening and closing odometer readings | L'ARC exige les lectures d'ouverture et de clôture |
| Simplified method | CRA simplified vehicle logbook method | Méthode du registre simplifié de l'ARC |
| CRA guides | T4002 / IT-521R / T2125 / T777 | Same — these are CRA publication numbers |
| Trip purpose placeholder | e.g. Client visit, medical appointment | p. ex. visite client, rendez-vous médical |
| Trip category — business | Business | Affaires |
| Trip category — medical | Medical | Médical |
| Trip category — charitable | Charitable | Bienfaisance |
| Empty logbook state | No CRA-eligible trips found | Aucun trajet admissible à l'ARC trouvé |
| Diagnostics CRA note | CRA-ready logbook / motor vehicle log | Registre de véhicule conforme à l'ARC |

---

## PR Review Steps

1. Search the diff for any banned term (case-insensitive grep: `IRS\|\bmiles\b\|mileage\|zip code\|Schedule C\|Form 1099`).
2. Confirm every user-visible distance value is suffixed with `km` — never `mi` or `miles`.
3. Confirm every currency display uses `$` with Canadian formatting (`1,234.56` EN; `1 234,56 $` FR).
4. Confirm any new string added to `res/values/strings.xml` has a matching entry in `res/values-fr/strings.xml`.
5. Confirm PDF/CSV export column headers and cover page copy use "vehicle logbook," "kilometric rate," and "CRA" — not "mileage log" or "IRS."
6. If a new screen is added, verify screen title, empty state, error message, and action labels are all backed by `strings.xml` entries (no hardcoded EN text in Compose files).

---

## Reference Documents

- [CRA Guide T4002 — Business and Professional Income](https://www.canada.ca/en/revenue-agency/services/forms-publications/publications/t4002.html)
- [CRA IT-521R — Motor Vehicle Expenses Claimed by Self-Employed Individuals](https://www.canada.ca/en/revenue-agency/services/forms-publications/publications/it521r.html)
- [CRA Automobile and Motor Vehicle Allowances (kilometric rates)](https://www.canada.ca/en/revenue-agency/services/tax/businesses/topics/payroll/benefits-allowances/automobile/automobile-motor-vehicle-allowances/automobile-allowance-rates.html)
- Canada Post Addressing Guidelines — postal code format `ANA NAN`
