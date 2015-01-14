# Customizing Validation Rules

The FhirProfileValidator loads an optional rule file (specified in the user config file)
that allows a profile author to specify the following conditions:

1. If a profiled element is, for example, a CodeableConcept and contains a binding
it is standard practice to specify a short label with the shortened list of codes
(e.g. "provisional | working | confirmed" for a restricted binding of Condition.status
in the cqf-condition profile). If the short label value was omitted in profile for
this element the following warning would be added to the validation report.

```
Condition
Profile: cqf-condition
WARN: element has binding and should specify a short value: Condition.status
```

To suppress this warning add the following rule into the rules file:

    cqf-condition.ignoreWarn=element has binding and should specify a short value: Condition.status

2. If profile changes the cardinality is changed for particular element from the base resource then
the change is flagged by the validator. Profiles cannot break the rules established in the base
specification (e.g. if the element cardinality is 1..1 in the base specification, a profile
cannot say it is 0..1, or 1..*). However, a profile can change 0..1 to 1..1 making an optional
element mandatory or 0..1 to 0..0 for ruling out use of that element. The latter are valid
profile uses, however, the change could have been entered as a typo or cut-paste error so it
is flagged with a blue highlight as informational in the validation report. This can be changed
to an acknowledged change (shown in GREEN) by adding a rule to the rule file.

    cqf-allergyintolerance-refuted.AllergyIntolerance.status.card=1..1