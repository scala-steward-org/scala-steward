package org.scalasteward.core.forge.gitlab

import munit.CatsEffectSuite
import org.http4s.Request
import org.scalasteward.core.TestInstances.ioLogger
import org.scalasteward.core.application.Config.{GitLabCfg, MergeRequestApprovalRulesCfg}
import org.scalasteward.core.data.Repo
import org.scalasteward.core.forge.ForgeType
import org.scalasteward.core.mock.MockConfig.config
import org.scalasteward.core.mock.MockContext.context.httpJsonClient
import org.scalasteward.core.mock.MockEff
import org.scalasteward.core.util.Nel

class GitLabAlgTest extends CatsEffectSuite {

  private val gitlabApiAlg = new GitLabApiAlg[MockEff](
    forgeCfg = config.forgeCfg.copy(tpe = ForgeType.GitLab),
    gitLabCfg = GitLabCfg(
      mergeWhenPipelineSucceeds = false,
      requiredApprovals = None,
      removeSourceBranch = true
    ),
    modify = (_: Repo) => (request: Request[MockEff]) => MockEff.pure(request)
  )

  test(
    "calculateRulesToUpdate -- ignore active approval rule that doesn't have approval rule configuration"
  ) {
    val activeApprovalRules =
      List(
        MergeRequestLevelApprovalRuleOut(name = "A", id = 101),
        MergeRequestLevelApprovalRuleOut(name = "B", id = 201)
      )
    val approvalRulesCfg =
      Nel.one(MergeRequestApprovalRulesCfg(approvalRuleName = "B", requiredApprovals = 1))

    val result =
      gitlabApiAlg.calculateRulesToUpdate(activeApprovalRules, approvalRulesCfg)
    val expectedResult =
      List(
        (
          MergeRequestApprovalRulesCfg(approvalRuleName = "B", requiredApprovals = 1),
          MergeRequestLevelApprovalRuleOut(id = 201, name = "B")
        )
      )

    assertEquals(result, expectedResult)
  }

  test(
    "calculateRulesToUpdate -- ignore approval rule configuration that doesn't have active approval rule"
  ) {
    val activeApprovalRules =
      List(MergeRequestLevelApprovalRuleOut(name = "A", id = 101))
    val approvalRulesCfg =
      Nel.of(
        MergeRequestApprovalRulesCfg(approvalRuleName = "A", requiredApprovals = 1),
        MergeRequestApprovalRulesCfg(approvalRuleName = "B", requiredApprovals = 2)
      )

    val result =
      gitlabApiAlg.calculateRulesToUpdate(activeApprovalRules, approvalRulesCfg)
    val expectedResult =
      List(
        (
          MergeRequestApprovalRulesCfg(approvalRuleName = "A", requiredApprovals = 1),
          MergeRequestLevelApprovalRuleOut(name = "A", id = 101)
        )
      )

    assertEquals(result, expectedResult)
  }
}
