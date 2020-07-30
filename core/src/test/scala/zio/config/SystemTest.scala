package zio.config

import zio.config.ConfigDescriptor._
import zio.random.Random
import zio.test.Assertion._
import zio.test.environment.TestEnvironment
import zio.test.{ DefaultRunnableSpec, _ }
import zio.{ UIO, ZIO }

object SystemTest extends DefaultRunnableSpec {

  def spec: Spec[TestEnvironment, TestFailure[Nothing], TestSuccess] =
    suite("Configuration from system")(
      testM("from system properties") {
        checkM(genSomeConfig, genDelimiter) { (config, delimiter) =>
          val result = for {
            _ <- setSystemProperties(config, delimiter)
            p <- ZIO.environment.provideLayer(Config.fromSystemProperties(SomeConfig.descriptor, Some(delimiter)))
            _ <- clearSystemProperties(delimiter)
          } yield p.get

          assertM(result.either)(isRight(equalTo(config)))
        }
      },
      testM("from system environment") {
        val config       = SomeConfig(100, "ABC")
        val keyDelimiter = '_'

        val sysEnvLayer =
          SystemModule.test(Map("SYSTEMPROPERTIESTEST_SIZE" -> "100", "SYSTEMPROPERTIESTEST_DESCRIPTION" -> "ABC"))
        val configEnvLayer = sysEnvLayer >>> Config.fromSystemEnv(SomeConfig.descriptor, Some(keyDelimiter))
        val result =
          ZIO.environment.provideLayer(configEnvLayer).map(_.get)

        assertM(result.either)(isRight(equalTo(config)))
      },
      testM("invalid system environment delimiter") {
        val keyDelimiter = '.'

        //TODO DRY
        val sysEnvLayer =
          SystemModule.test(Map("SYSTEMPROPERTIESTEST_SIZE" -> "100", "SYSTEMPROPERTIESTEST_DESCRIPTION" -> "ABC"))
        val configEnvLayer = sysEnvLayer >>> Config.fromSystemEnv(SomeConfig.descriptor, Some(keyDelimiter))
        val result =
          ZIO.environment.provideLayer(configEnvLayer).map(_.get)

        assertM(result.either)(
          isLeft(
            equalTo(
              ReadError.SourceError[String](
                message = s"Invalid system key delimiter: $keyDelimiter",
                annotations = Set.empty
              )
            )
          )
        )
      }
    )

  final case class SomeConfig(size: Int, description: String)

  object SomeConfig {
    val descriptor: ConfigDescriptor[SomeConfig] =
      nested("SYSTEMPROPERTIESTEST")(
        (int("SIZE") |@| string("DESCRIPTION"))(SomeConfig.apply, SomeConfig.unapply)
      )
  }

  def genSomeConfig: Gen[Random with Sized, SomeConfig] =
    for {
      size <- Gen.anyInt
      desc <- Gen.anyString
    } yield SomeConfig(size, desc)

  def genDelimiter: Gen[Random, Char]       = Gen.elements('.', '_', '-', ':')
  def genSystemDelimiter: Gen[Random, Char] = Gen.elements('_')

  def setSystemProperties(config: SomeConfig, delimiter: Char): UIO[String] = ZIO.succeed {
    java.lang.System.setProperty(s"SYSTEMPROPERTIESTEST${delimiter}SIZE", config.size.toString)
    java.lang.System.setProperty(s"SYSTEMPROPERTIESTEST${delimiter}DESCRIPTION", config.description)
  }

  def clearSystemProperties(delimiter: Char): UIO[String] = ZIO.succeed {
    java.lang.System.clearProperty(s"SYSTEMPROPERTIESTEST${delimiter}SIZE")
    java.lang.System.clearProperty(s"SYSTEMPROPERTIESTEST${delimiter}DESCRIPTION")
  }

}
