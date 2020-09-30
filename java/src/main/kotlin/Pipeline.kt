import dependencies.Config
import dependencies.Emailer
import dependencies.Logger
import dependencies.Project
import io.vavr.control.Try

typealias PipelineStep = (Project) -> Try<String>

class Pipeline(private val config: Config,
               private val emailer: Emailer,
               private val log: Logger) {
  class TestFailedError : RuntimeException("Tests failed")
  class DeploymentFailedError : RuntimeException("Deployment failed")

  private val pipelineSteps = listOf<PipelineStep>(::runTests, ::deploy)

  fun run(project: Project) {
    Try.traverse(pipelineSteps) { runStepOn -> runStepOn(project).onSuccess(log::info) }
      .map { it.last() }
      .onFailure(::logError)
      .recover(Throwable::message)
      .flatMap(::sendEmailNotification)
      .onSuccess(log::info)
  }

  private fun logError(throwable: Throwable) = log.error(throwable.message)

  private fun runTests(project: Project): Try<String> = when {
    !project.hasTests()                -> Try.success("No tests")
    !project.runTests().isSuccessful() -> Try.failure(TestFailedError())
    else                               -> Try.success("Tests passed")
  }

  private fun deploy(project: Project): Try<String> = when {
    !project.deploy().isSuccessful() -> Try.failure(DeploymentFailedError())
    else                             -> Try.success("Deployment successful")
  }

  private fun String.isSuccessful() = "success" == this

  private fun sendEmailNotification(message: String): Try<String> {
    if (!config.sendEmailSummary()) {
      return Try.success("Email disabled")
    }
    emailer.send(message)
    return Try.success("Sending email")
  }
}