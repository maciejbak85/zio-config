package zio.config

import zio.{ Has, Task, ULayer, ZIO, ZLayer }

object SystemModule {
  type SystemModule = Has[SystemModule.Service]

  trait Service {
    def getEnvironment: Task[Map[String, String]]
  }

  def live: ULayer[SystemModule] =
    ZLayer.succeed(new Service {
      override def getEnvironment: Task[Map[String, String]] = Task.effect(sys.env)
    })

  def test(envMap: Map[String, String] = Map.empty) =
    ZLayer.succeed(new Service {
      override def getEnvironment: Task[Map[String, String]] = Task.effectTotal(envMap)
    })

  def getEnvironment: ZIO[SystemModule, Throwable, Map[String, String]] =
    ZIO.accessM(_.get.getEnvironment)

}
