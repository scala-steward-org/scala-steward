module.exports = {
    "dataSource": "prs",
    "prefix": "",
    "onlyMilestones": true,
    "groupBy": {
        "Enhancements": ["enhancement"],
        "Bug Fixes": ["bug"],
        "Scalafix Migrations": ["scalafix-migration"],
        "Documentation": ["documentation"],
        "Test Improvements": ["testing"],
        "Build Improvements": ["build"],
        "Refactorings": ["refactoring"],
        "Dependency Updates": ["dependency-update"]
    },
    "changelogFilename": "CHANGELOG.md",
    "template": {
      issue: "- {{name}} [{{text}}]({{url}}) by [@{{user_login}}]({{user_url}})"
    }
}
