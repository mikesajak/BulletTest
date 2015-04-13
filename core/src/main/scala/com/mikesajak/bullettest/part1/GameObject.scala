package com.mikesajak.bullettest.part1

import com.badlogic.gdx.graphics.g3d.{Model, ModelInstance}
import com.badlogic.gdx.physics.bullet.collision.{btCollisionObject, btCollisionShape}
import com.badlogic.gdx.utils.Disposable

/**
 * Created by mike on 11.04.15.
 */
class GameObject(model: Model, val node: String, val shape: btCollisionShape)
  extends ModelInstance(model, node) with Disposable {
  val body: btCollisionObject = new btCollisionObject()
  body.setCollisionShape(shape)

  var moving = true

  override def dispose() = {
    body.dispose()
  }
}

object GameObject {

  class Builder(model: Model, node: String, shape: btCollisionShape) extends Disposable {
    def build() = new GameObject(model, node, shape)

    override def dispose() = shape.dispose()
  }


  class SampleObjects {

  }
}


