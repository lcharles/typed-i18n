# Shared vocabulary, private to this catalog.
-app-name = typed-i18n

common-save = Save
account-welcome = Welcome, { $userName }, to { -app-name }!

# $count is an Int supplied by the application.
survey-answerCount =
    { $count ->
        [0] No answers yet
        [one] { $count } answer
       *[other] { $count } answers
    }
