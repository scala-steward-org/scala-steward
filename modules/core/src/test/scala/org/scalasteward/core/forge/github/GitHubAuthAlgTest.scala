package org.scalasteward.core.forge.github

import better.files.File
import cats.effect.IO
import munit.CatsEffectSuite

import scala.concurrent.duration._

class GitHubAuthAlgTest extends CatsEffectSuite {

  private val gitHubAuthAlg = GitHubAuthAlg.create[IO]
  private val pemFile = File(getClass.getResource("/rsa-4096-private.pem"))
  private val nowMillis = 1673743729714L

  test("createJWT with ttl") {
    val obtained = gitHubAuthAlg.createJWT(GitHubApp(42, pemFile), 2.minutes, nowMillis)
    val expected =
      "eyJhbGciOiJSUzI1NiJ9.eyJpYXQiOjE2NzM3NDM3MjksImlzcyI6IjQyIiwiZXhwIjoxNjczNzQzODQ5fQ.SDW4TqjokzYAwHD6joDdgqCtQyPrq-4QThanWB12vNUkjNtP4gw9iiG_baWBNXi4nlA6_HtO0H_WNKO6God6vkHz_ERBbIUb7I2vhp17NEb8vRECUksqARnrAzPU8HPUZPD5V7uehEDxEa-Tv-eI3L8iH8JVWx-m60vAZdBi76IQ094mIXf_d1TC75HKpap1wPMV7i_973IVAuL6zu2Sy6bkhHAS0WAQKStSAolFvwih7uq2f6N1b-1ogopFtkL6w19lQ4iRSvaoXPvkyBuvw6DqowVcAWon8-OB9cdzUIsjQs5GkR4IwCQQOBp-9_NYKBRDyVTwa-vqBBlYcOc_Zzd-_tpK3zRLpsh-h8_p0W8YAQrYAVyJRWn128Mm72jc2q9DkWhsiIGGWr44p3z6DENypgx3HiFDZbcvgMhPJKeNY3CwYh2QK56XtPNcbYSmUzog1IkX5lrM3WOO9j1bfj8tTP5h46dYXApvTq2-q5zlLP66Rm40RQnc_TE_6ntVq1kKn6IQ0yqEuPN0GVwoX71PElnajufz_Bzn08-YtYMK2Ca-t-wKWapDaH9zDjWUoXe_Pbcb5T_AZkbqPy8MHkzRzkMFSACwrXjHDuq_PphdlHZJeIb4xJ0PSp4f6urz_TRdxFmrTlG-e7DaKcoOLMbp8VK419TD3VinXq3MGDs"
    assertIO(obtained, expected)
  }

  test("createJWT without ttl") {
    val obtained = gitHubAuthAlg.createJWT(GitHubApp(42, pemFile), 0.minutes, nowMillis)
    val expected =
      "eyJhbGciOiJSUzI1NiJ9.eyJpYXQiOjE2NzM3NDM3MjksImlzcyI6IjQyIn0.GcJ2RzzwgN-decPz0BNhNwrMFh6Wjj2xtbH0bOWEBGolnclEymJDT0QrjojvVw7iDabq5FezOGgPYP6JXlykMQlXFjX7TFeBAsydpZt1wyU1N8PQwxpoUtumksBGgTqNuIWg6_Y8CQg-UTbM4B63axcNREz6iT43a0cKxNe0ABy6jwcWSXw2Ck5Ob2uS_ZMCAt3VapIovT7Vci0goI7z6eXF8l6FpJauSgiVRXYsOAoZwXnDeNU1LkWFkGtWh9vK4iyaI_IDc85f3ODU5KfiPHOWuy2h7j6WPKEMXQTLXiiGQr_HqP4ROR-HXW7hlpyBFsrL44EqNe3oQcnTWNdOAj2s2K0aLzMm1XmeenPKgMeJcDvp8q_lRFKC54En4bHKZZEccOVnfItEb7D7fkBuWUYM5-k6cb4CPZyPrOvO5zBsQyboW2_Zcrpr_mGelm9rdSQ29azIvu2G2gBWY_QsT54E1_D3uN4HbsUsTxwjJPXlw2ScFgn_4wGu3XuU9QfIzipw4-PJtXo9deoHMinji0VuXzAZslJMyCoKqvCOV7voVNQOuQJroVeahVY1cU-dWLWOfrOcJ0LZRxZ2gIoRztc1wawfmNix8mFGNXei_qY0M5LZtOgWfdgIsmrUF17s1mX2Lwp2mlvjvCCP6qcXQnrn6GWit_ihcOb2IFR9yIw"
    assertIO(obtained, expected)
  }
}
