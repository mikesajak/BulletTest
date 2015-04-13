package com.mikesajak.bullettest.part1

import com.badlogic.gdx.graphics.VertexAttributes.Usage
import com.badlogic.gdx.graphics.g3d._
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.graphics.g3d.utils.{CameraInputController, ModelBuilder}
import com.badlogic.gdx.graphics.{Color, GL20, PerspectiveCamera}
import com.badlogic.gdx.math.{MathUtils, Vector3}
import com.badlogic.gdx.physics.bullet.Bullet
import com.badlogic.gdx.physics.bullet.collision._
import com.badlogic.gdx.{ApplicationAdapter, Gdx}
import com.mikesajak.bullettest.part1.GameObject

import scala.collection.JavaConversions._

/**
 * Created by mike on 06.04.15.
 */
class BulletTest extends ApplicationAdapter {

  var cam: PerspectiveCamera = _
  var camController: CameraInputController = _
  var modelBatch: ModelBatch = _
//  var instances: List[ModelInstance] = _
  var environment: Environment = _
  var model: Model = _
//  var ground: ModelInstance = _
//  var ball: ModelInstance = _

//  var collision = false
//  var groundShape: btCollisionShape = _
//  var groundObject: btCollisionObject = _
//  var ballShape: btCollisionShape = _
//  var ballObject: btCollisionObject = _

  var gameObjects = IndexedSeq[GameObject]()
  var gameObjectConstructors: Map[String, GameObject.Builder] = _
  var gameObjectConstructorsIdx: IndexedSeq[String] = _

  var groundObject: GameObject = _

  var collisionConfig: btCollisionConfiguration = _
  var dispatcher: btDispatcher = _
  
  var spawnTimer = 0f

  var contactListener: ContactListener = _

  override def create() = {
    Bullet.init()

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
    

//    ground = new ModelInstance(model, "ground")
//    ball = new ModelInstance(model, "ball")
//    ball.transform.setToTranslation(0, 9f, 0)

//    instances = List(ground, ball)

    // setup physics engine

    collisionConfig = new btDefaultCollisionConfiguration()
    dispatcher = new btCollisionDispatcher(collisionConfig)

    contactListener = new ContactListener() {
      // first attempt - it overrides onContactAdded with unnecessary wrappers that are not used - waste of time and resources
      // because bullet has to cretae those C++/Java wrappers
//      override def onContactAdded(cp: btManifoldPoint, colObj0Wrap: btCollisionObjectWrapper, partId0: Int, index0: Int,
//                                  colObj1Wrap: btCollisionObjectWrapper, partId1: Int,
//                                  index1: Int) = {
//
//        val idx0 = colObj0Wrap.getCollisionObject.getUserValue
//        val idx1 = colObj1Wrap.getCollisionObject.getUserValue
//
//        println(s"Collision detected: $idx0 <-> $idx1, collision manifold: $cp, colObj0Wrap: $colObj0Wrap, " +
//                  s"index0: $index0, partId0: $partId0, colObj1Wrap: $colObj1Wrap, index1: $index1, partId1: $partId1")
//
//        gameObjects(idx0).moving = false
//        gameObjects(idx1).moving = false
//        true
//      }

      // second approach - better as there is less not used objects, but still we can do better :)
      // we don't really use the object wrapper - just the original object
//      override def onContactAdded(colObj0Wrap: btCollisionObjectWrapper, partId0: Int, index0: Int,
//                                    colObj1Wrap: btCollisionObjectWrapper, partId1: Int, index1: Int) = {
//        val idx0 = colObj0Wrap.getCollisionObject.getUserValue
//        val idx1 = colObj1Wrap.getCollisionObject.getUserValue
//
//        println(s"Collision detected: $idx0 <-> $idx1")
//
//        gameObjects(idx0).moving = false
//        gameObjects(idx1).moving = false
//        true
//      }

      // third approach - avoid collisionObjectWrapper use the object itself
//      override def onContactAdded(colObj0: btCollisionObject, partId0: Int, index0: Int,
//                                  colObj1: btCollisionObject, partId1: Int, index1: Int) = {
//        val idx0 = colObj0.getUserValue
//        val idx1 = colObj1.getUserValue
//
//        println(s"Collision detected: $idx0 <-> $idx1")
//
//        gameObjects(idx0).moving = false
//        gameObjects(idx1).moving = false
//        true
//      }

      // fourth approach - in our case we don't need any object at all, just the user value
      override def onContactAdded(userValue0: Int, partId0: Int, index0: Int, userValue1: Int, partId1: Int,
                                  index1: Int) = {
        println(s"Collision detected: $userValue0<-> $userValue1")

        gameObjects(userValue0).moving = false
        gameObjects(userValue1).moving = false
        true
      }
    }

//    ballShape = new btSphereShape(0.5f)
//    ballObject = new btCollisionObject()
//    ballObject.setCollisionShape(ballShape)
//    ballObject.setWorldTransform(ball.transform)
//
//    groundShape = new btBoxShape(new Vector3(2.5f, 0.5f, 2.5f))
//    groundObject = new btCollisionObject()
//    groundObject.setCollisionShape(groundShape)
//    groundObject.setWorldTransform(ground.transform)

  }

  override def dispose() = {
//    groundObject.dispose()
//    groundShape.dispose()
//
//    ballObject.dispose()
//    ballShape.dispose()
    gameObjects.foreach(_.dispose())
    gameObjectConstructors.foreach(_._2.dispose())

    contactListener.dispose()

    dispatcher.dispose()
    collisionConfig.dispose()

    modelBatch.dispose()
    model.dispose()
  }

  override def render() = {
    val delta = math.min(1f/30f, Gdx.graphics.getDeltaTime)
    
    for (obj <- gameObjects if obj.moving) {
//      println(s"Processing: ${obj.node} moving=${obj.moving}")
      obj.transform.trn(0f, -delta*2, 0f)
      obj.body.setWorldTransform(obj.transform)
      checkCollision(obj.body, groundObject.body)
    }
    
    spawnTimer -= delta
    if (spawnTimer < 0) {
      spawn()
      spawnTimer = 1.5f
    }

//    if (!collision) {
//      ball.transform.translate(0f, -delta, 0f)
//      ballObject.setWorldTransform(ball.transform)
//      collision = checkCollision(ballObject, groundObject)
//    }

    camController.update()

    Gdx.gl.glClearColor(0.3f, 0.3f, 0.3f, 1f)
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT)

    modelBatch.begin(cam)
    modelBatch.render(gameObjects, environment)
    modelBatch.end()
  }
  
  def spawn() = {
    import MathUtils._

    val objName = gameObjectConstructorsIdx(1 + random(gameObjectConstructorsIdx.size - 2))
    val obj= gameObjectConstructors(objName).build()
    obj.moving = true
    obj.transform.setFromEulerAngles(random(360f), random(360f), random(350f))
    obj.transform.trn(random(-2.5f, 2.5f), 9f, random(-2.5f, 2.5f))
    obj.body.setWorldTransform(obj.transform)
    obj.body.setUserValue(gameObjects.size)
    obj.body.setCollisionFlags(obj.body.getCollisionFlags | btCollisionObject.CollisionFlags.CF_CUSTOM_MATERIAL_CALLBACK)
    gameObjects :+= obj
  }


  def checkCollision(obj0: btCollisionObject, obj1: btCollisionObject) = {
    val co0 = new CollisionObjectWrapper(obj0)
    val co1 = new CollisionObjectWrapper(obj1)

//    val ci = new btCollisionAlgorithmConstructionInfo()
//    ci.setDispatcher1(dispatcher)
//    val algorithm = new btSphereBoxCollisionAlgorithm(null, ci, co0.wrapper, co1.wrapper, false)
    val algorithm = dispatcher.findAlgorithm(co0.wrapper, co1.wrapper)
    val info = new btDispatcherInfo()
    val result = new btManifoldResult(co0.wrapper, co1.wrapper)

    algorithm.processCollision(co0.wrapper, co1.wrapper, info, result)

    val r = result.getPersistentManifold.getNumContacts() > 0

    dispatcher.freeCollisionAlgorithm(algorithm.getCPointer)
    result.dispose()
    info.dispose()
//    ci.dispose()
    co1.dispose()
    co0.dispose()

    r
  }
}
