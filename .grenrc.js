module.exports = {
    "dataSource": "prs",
    "prefix": "",
    "onlyMilestones": true,
    "groupBy": {
        "Enhancements": ["enhancement"],
        "Bug Fixes": ["bug"],
        "Documentation": ["doc"],
        "Test Improvements": ["testing"],
        "Build Improvements": ["build"],
        "Dependency Updates": ["dependency-update"],
        "Refactorings": ["refactoring"]
    },
    "changelogFilename": "CHANGELOG.md",
    "template": {
      issue: "- {{name}} [{{text}}]({{url}})"
    }
}
