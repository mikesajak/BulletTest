package com.mikesajak.bullettest.desktop

import com.badlogic.gdx.backends.lwjgl.{LwjglApplication, LwjglApplicationConfiguration}
import com.mikesajak.bullettest.BulletTestJava
import com.mikesajak.bullettest.part1.BulletTest2
import com.mikesajak.bullettest.part2.RigidBodyTest

/**
 * Created by mike on 06.04.15.
 */
object DesktopLauncher {
  def main(args: Array[String]): Unit = {
    val config = new LwjglApplicationConfiguration
    new LwjglApplication(new RigidBodyTest, config)
  }
}
