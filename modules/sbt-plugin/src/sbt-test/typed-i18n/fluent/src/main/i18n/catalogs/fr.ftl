# Category labels make the integration assertions independent of translation prose.
-greeting = Hello
-role = { $form ->
    [short] friend
   *[long] dear friend
    }

save-button = Save
common-reference = { save-button } / { save-button }
common-constant = Constant
account-welcome = { $tone ->
    [formal] { -greeting }, { $userName }, { -role(form: "long") }!
   *[casual] Hi, { $userName }
    }

survey-answerCount = { $count ->
    [zero] zero
    [one] one
    [two] two
    [few] few
    [many] many
   *[other] other
    }

metrics-decimal = { $value ->
    [one] one
    [few] few
    [many] many
   *[other] other
    }

metrics-large = { $value }
