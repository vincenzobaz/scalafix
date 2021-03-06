package scala.tools.nsc.interactive

import java.util.logging.Level

import scala.tools.nsc.util.WorkScheduler
import scala.util.control.NonFatal

import scala.meta.internal.pc.ScalafixGlobal

trait ScalafixGlobalProxy { this: ScalafixGlobal =>
  def presentationCompilerThread: Thread = this.compileRunner
  def hijackPresentationCompilerThread(): Unit = newRunnerThread()
  def isHijacked(): Boolean =
    presentationCompilerThread.isInstanceOf[ScalafixGlobalThread]

  def metalsAsk[T](fn: Response[T] => Unit): T = {
    val r = new Response[T]
    fn(r)
    r.get match {
      case Left(value) =>
        value
      case Right(value) =>
        throw value
    }
  }

  /**
   * Shuts down the default presentation compiler thread and replaces it with a custom implementation.
   */
  private def newRunnerThread(): Thread = {
    if (compileRunner.isAlive) {
      try {
        this.askShutdown()
        while (compileRunner.isAlive) Thread.sleep(0)
      } catch {
        case NonFatal(e) =>
          println(
            Level.INFO,
            "unexpected error shutting down presentation compiler thread",
            e
          )
      }
    }
    this.scheduler = new WorkScheduler
    compileRunner = new ScalafixGlobalThread(this, "Metals")
    compileRunner.setDaemon(true)
    compileRunner.start()
    compileRunner
  }
}
