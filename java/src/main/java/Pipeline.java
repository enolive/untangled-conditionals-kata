import dependencies.Config;
import dependencies.Emailer;
import dependencies.Logger;
import dependencies.Project;
import io.vavr.collection.List;
import io.vavr.collection.Traversable;
import io.vavr.control.Try;

import java.util.function.Function;

class TestFailedError extends RuntimeException {
  public TestFailedError() {
    super("Tests failed");
  }
}

class DeploymentFailedError extends RuntimeException {
  public DeploymentFailedError() {
    super("Deployment failed");
  }
}

public class Pipeline {
  private final Config config;
  private final Emailer emailer;
  private final Logger log;
  private final List<Function<Project, Try<String>>> pipelineSteps = List.of(this::runTests, this::deploy);

  public Pipeline(Config config, Emailer emailer, Logger log) {
    this.config = config;
    this.emailer = emailer;
    this.log = log;
  }

  public void run(Project project) {
    Try.traverse(pipelineSteps, runStepOn(project))
       .map(Traversable::last)
       .onFailure(this::logError)
       .recover(Throwable::getMessage)
       .flatMap(this::sendEmailNotification)
       .onSuccess(log::info);
  }

  private Function<Function<Project, Try<String>>, Try<? extends String>> runStepOn(Project project) {
    return pipelineStep -> pipelineStep.apply(project).onSuccess(log::info);
  }

  private void logError(Throwable throwable) {
    log.error(throwable.getMessage());
  }

  private Try<String> runTests(Project project) {
    if (!project.hasTests()) {
      return Try.success("No tests");
    }
    if (!isSuccessful(project.runTests())) {
      return Try.failure(new TestFailedError());
    }
    return Try.success("Tests passed");
  }

  private Try<String> deploy(Project project) {
    if (!isSuccessful(project.deploy())) {
      return Try.failure(new DeploymentFailedError());
    }
    return Try.success("Deployment successful");
  }

  @SuppressWarnings("BooleanMethodIsAlwaysInverted")
  private boolean isSuccessful(String s) {
    return "success".equals(s);
  }

  private Try<String> sendEmailNotification(String message) {
    if (!config.sendEmailSummary()) {
      return Try.success("Email disabled");
    }
    emailer.send(message);
    return Try.success("Sending email");
  }
}
