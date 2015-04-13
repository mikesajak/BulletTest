package com.mikesajak.bullettest.part1

import com.badlogic.gdx.graphics.VertexAttributes.Usage
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.graphics.g3d.utils.{CameraInputController, ModelBuilder}
import com.badlogic.gdx.graphics.g3d.{Environment, Model, ModelBatch, _}
import com.badlogic.gdx.graphics.{Color, GL20, PerspectiveCamera}
import com.badlogic.gdx.math.MathUtils._
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.physics.bullet.Bullet
import com.badlogic.gdx.physics.bullet.collision._
import com.badlogic.gdx.{ApplicationAdapter, Gdx}
import com.mikesajak.bullettest.part1.GameObject

import scala.collection.JavaConversions._

/**
 * Created by mike on 11.04.15.
 */
class BulletTest2 extends ApplicationAdapter {

  var cam: PerspectiveCamera = _
  var camController: CameraInputController = _
  var modelBatch: ModelBatch = _
  var environment: Environment = _
  var model: Model = _

  var gameObjects = IndexedSeq[GameObject]()
  var gameObjectConstructors: Map[String, GameObject.Builder] = _
  var gameObjectConstructorsIdx: IndexedSeq[String] = _

  var groundObject: GameObject = _

  var collisionConfig: btCollisionConfiguration = _
  var dispatcher: btDispatcher = _

  var broadPhase: btBroadphaseInterface = _
  var collisionWorld: btCollisionWorld = _

  var spawnTimer = 0f

  var contactListener: ContactListener = _

  val GROUND_FLAG = (1 << 8).toShort
  val OBJECT_FLAG = (1 << 9).toShort
  val ALL_FLAG = -1.toShort


  override def create() = {
    setupPhysicsEngine()

    modelBatch = new ModelBatch()

    environment = new Environment()
    environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.4f, 0.4f, 0.4f, 1f))
    environment.add(new DirectionalLight().set(0.8f, 0.8f, 0.8f, -1f, -0.8f, -0.2f))

    cam = new PerspectiveCamera(67, Gdx.graphics.getWidth, Gdx.graphics.getHeight)
    cam.position.set(3f, 7f, 10f)
    cam.lookAt(0, 4f, 0)
    cam.update()

    camController = new CameraInputController(cam)
    Gdx.input.setInputProcessor(camController)


    def mkPart(id: String, diffuse: Color)(implicit mb: ModelBuilder) = {
      mb.node.id = id
      mb.part(id, GL20.GL_TRIANGLES, Usage.Position | Usage.Normal,
              new Material(ColorAttribute.createDiffuse(diffuse)))
    }

    implicit val mb = new ModelBuilder()
    mb.begin()
    mkPart("ground", Color.RED).box(5f, 1f, 5f)
    mkPart("sphere", Color.GREEN).sphere(1f, 1f, 1f, 10, 10)
    mkPart("box", Color.BLUE).box(1f, 1f, 1f)
    mkPart("cone", Color.YELLOW).cone(1f, 2f, 1f, 10)
    mkPart("capsule", Color.CYAN).capsule(0.5f, 2f, 10)
    mkPart("cylinder", Color.MAGENTA).cylinder(1f, 2f, 1f, 10)
    model = mb.end()

    gameObjectConstructors = Map(
      "ground"   -> new GameObject.Builder(model, "ground",   new btBoxShape(new Vector3(2.5f, 0.5f, 2.5f))),
      "sphere"   -> new GameObject.Builder(model, "sphere",   new btSphereShape(0.5f)),
      "box"      -> new GameObject.Builder(model, "box",      new btBoxShape(new Vector3(0.5f, 0.5f, 0.5f))),
      "cone"     -> new GameObject.Builder(model, "cone",     new btConeShape(0.5f, 2f)),
      "capsule"  -> new GameObject.Builder(model, "capsule",  new btCapsuleShape(0.5f, 1f)),
      "cylinder" -> new GameObject.Builder(model, "cylinder", new btCylinderShape(new Vector3(0.5f, 1f, 0.5f)))
    )

    gameObjectConstructorsIdx = gameObjectConstructors.keys.filter(k => k != "ground").toIndexedSeq

    groundObject = gameObjectConstructors("ground").build()
    groundObject.moving = false
    gameObjects :+= groundObject

    collisionWorld.addCollisionObject(groundObject.body, GROUND_FLAG, ALL_FLAG)

  }

  private def setupPhysicsEngine() = {
    Bullet.init()
    collisionConfig = new btDefaultCollisionConfiguration()
    dispatcher = new btCollisionDispatcher(collisionConfig)
    broadPhase = new btDbvtBroadphase()
    collisionWorld = new btCollisionWorld(dispatcher, broadPhase, collisionConfig)

    contactListener = new ContactListener() {
      override def onContactAdded(userValue0: Int, partId0: Int, index0: Int, userValue1: Int, partId1: Int,
                                  index1: Int) = {
        if (userValue1 == 0) {
          gameObjects(userValue0).moving = false
          println(s"Collision with ground detected: $userValue0<-> $userValue1")
        }
        if (userValue0 == 0) {
          gameObjects(userValue1).moving = false
          println(s"Collision with ground detected: $userValue0<-> $userValue1")
        }
        true
      }
    }
  }

  override def dispose() = {
    contactListener.dispose()

    collisionWorld.dispose()
    broadPhase.dispose()

    dispatcher.dispose()
    collisionConfig.dispose()

    gameObjects.foreach(_.dispose())
    gameObjectConstructors.foreach(_._2.dispose())

    modelBatch.dispose()
    model.dispose()
  }

  override def render() = {
    val delta = math.min(1f/30f, Gdx.graphics.getDeltaTime)

    for (obj <- gameObjects if obj.moving) {
      //      println(s"Processing: ${obj.node} moving=${obj.moving}")
      obj.transform.trn(0f, -delta*2, 0f)
      obj.body.setWorldTransform(obj.transform)
//      checkCollision(obj.body, groundObject.body)
    }

    collisionWorld.performDiscreteCollisionDetection()

    spawnTimer -= delta
    if (spawnTimer < 0) {
      spawn()
      spawnTimer = 1.5f
    }

    camController.update()

    Gdx.gl.glClearColor(0.3f, 0.3f, 0.3f, 1f)
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT)

    modelBatch.begin(cam)
    modelBatch.render(gameObjects, environment)
    modelBatch.end()
  }

  def spawn() = {

    val objName = gameObjectConstructorsIdx(1 + random(gameObjectConstructorsIdx.size - 2))
    val obj= gameObjectConstructors(objName).build()
    obj.moving = true
    obj.transform.setFromEulerAngles(random(360f), random(360f), random(350f))
    obj.transform.trn(random(-2.5f, 2.5f), 9f, random(-2.5f, 2.5f))
    obj.body.setWorldTransform(obj.transform)
    obj.body.setUserValue(gameObjects.size)
    obj.body.setCollisionFlags(obj.body.getCollisionFlags | btCollisionObject.CollisionFlags.CF_CUSTOM_MATERIAL_CALLBACK)
    gameObjects :+= obj

    collisionWorld.addCollisionObject(obj.body, OBJECT_FLAG, GROUND_FLAG)
  }


  def checkCollision(obj0: btCollisionObject, obj1: btCollisionObject) = {
    val co0 = new CollisionObjectWrapper(obj0)
    val co1 = new CollisionObjectWrapper(obj1)

    val algorithm = dispatcher.findAlgorithm(co0.wrapper, co1.wrapper)
    val info = new btDispatcherInfo()
    val result = new btManifoldResult(co0.wrapper, co1.wrapper)

    algorithm.processCollision(co0.wrapper, co1.wrapper, info, result)

    val r = result.getPersistentManifold.getNumContacts() > 0

    dispatcher.freeCollisionAlgorithm(algorithm.getCPointer)
    result.dispose()
    info.dispose()
    co1.dispose()
    co0.dispose()

    r
  }
}

