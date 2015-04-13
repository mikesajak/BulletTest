package com.mikesajak.bullettest.part2

import com.badlogic.gdx.graphics.VertexAttributes.Usage
import com.badlogic.gdx.graphics.g3d._
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.graphics.g3d.utils.{CameraInputController, ModelBuilder}
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType
import com.badlogic.gdx.graphics.{Color, GL20, PerspectiveCamera}
import com.badlogic.gdx.math.MathUtils._
import com.badlogic.gdx.math.{Matrix4, Vector3}
import com.badlogic.gdx.physics.bullet.{DebugDrawer, Bullet}
import com.badlogic.gdx.physics.bullet.collision._
import com.badlogic.gdx.physics.bullet.dynamics._
import com.badlogic.gdx.physics.bullet.dynamics.btRigidBody.btRigidBodyConstructionInfo
import com.badlogic.gdx.physics.bullet.linearmath.{btIDebugDraw, btTransform, btMotionState}
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.{ApplicationAdapter, Gdx}

import collection.JavaConversions._

/**
 * Created by mike on 11.04.15.
 */
class RigidBodyTest extends ApplicationAdapter {
  var cam: PerspectiveCamera = _
  var camController: CameraInputController = _
  var modelBatch: ModelBatch = _
  var environment: Environment = _
  var model: Model = _

  var gameObjects = IndexedSeq[GameObjectPhis]()
  var gameObjectConstructors: Map[String, GameObjectPhis.Builder] = _
  var gameObjectConstructorsIdx: IndexedSeq[String] = _

  var groundObject: GameObjectPhis = _

  var collisionConfig: btCollisionConfiguration = _
  var dispatcher: btDispatcher = _

  var broadPhase: btBroadphaseInterface = _
//  var collisionWorld: btCollisionWorld = _
  var dynamicsWorld: btDynamicsWorld = _
  var constraintSolver: btConstraintSolver = _

  var spawnTimer = 0f

  var contactListener: ContactListener = _

  var constraints = List[btTypedConstraint]()

  val GROUND_FLAG = (1 << 8).toShort
  val OBJECT_FLAG = (1 << 9).toShort
  val ALL_FLAG = -1.toShort

  var debugDrawer: DebugDrawer = _


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
      "ground"   -> new GameObjectPhis.Builder(model, "ground",   new btBoxShape(new Vector3(2.5f, 0.5f, 2.5f)), 0f),
      "sphere"   -> new GameObjectPhis.Builder(model, "sphere",   new btSphereShape(0.5f), 1f),
      "box"      -> new GameObjectPhis.Builder(model, "box",      new btBoxShape(new Vector3(0.5f, 0.5f, 0.5f)), 1f),
      "cone"     -> new GameObjectPhis.Builder(model, "cone",     new btConeShape(0.5f, 2f), 1f),
      "capsule"  -> new GameObjectPhis.Builder(model, "capsule",  new btCapsuleShape(0.5f, 1f), 1f),
      "cylinder" -> new GameObjectPhis.Builder(model, "cylinder", new btCylinderShape(new Vector3(0.5f, 1f, 0.5f)), 1f)
    )

    gameObjectConstructorsIdx = gameObjectConstructors.keys.filter(k => k != "ground").toIndexedSeq

    groundObject = gameObjectConstructors("ground").build()
    groundObject.body.setCollisionFlags(groundObject.body.getCollisionFlags | btCollisionObject.CollisionFlags.CF_KINEMATIC_OBJECT)
    groundObject.body.setActivationState(CollisionConstants.DISABLE_DEACTIVATION)
    gameObjects :+= groundObject

//    dynamicsWorld.addRigidBody(groundObject.body, GROUND_FLAG, ALL_FLAG)
    dynamicsWorld.addRigidBody(groundObject.body)
    groundObject.body.setContactCallbackFlag(GROUND_FLAG)
    groundObject.body.setContactCallbackFilter(0)

  }

  private def setupPhysicsEngine() = {
    Bullet.init()
    collisionConfig = new btDefaultCollisionConfiguration()
    dispatcher = new btCollisionDispatcher(collisionConfig)
    broadPhase = new btDbvtBroadphase()
    constraintSolver = new btSequentialImpulseConstraintSolver()
    dynamicsWorld = new btDiscreteDynamicsWorld(dispatcher, broadPhase, constraintSolver, collisionConfig)
    dynamicsWorld.setGravity(new Vector3(0, -10f, 0))

    debugDrawer= new DebugDrawer()
    import btIDebugDraw.DebugDrawModes._
    debugDrawer.setDebugMode(DBG_DrawConstraints | DBG_DrawContactPoints | DBG_DrawFeaturesText |
                             DBG_DrawText | DBG_EnableSatComparison)
    dynamicsWorld.setDebugDrawer(debugDrawer)



    contactListener = new ContactListener() {
//      override def onContactAdded(userValue0: Int, partId0: Int, index0: Int, userValue1: Int, partId1: Int,
//                                  index1: Int) = {

      override def onContactAdded(userValue0: Int, partId0: Int, index0: Int, match0: Boolean, userValue1: Int,
                                  partId1: Int, index1: Int, match1: Boolean) = {

//        println(s"Collision detected: $userValue0<-> $userValue1")
//        if (userValue0 != 0) {
        if (match0) {
          val ob0Diffuse = gameObjects(userValue0).materials.get(0).get(
            ColorAttribute.Diffuse).asInstanceOf[ColorAttribute]
          ob0Diffuse.color.set(Color.WHITE) // new Color(random(), random(), random(), 1f))
        }

//        if (userValue1 != 0) {
        if (match1) {
          val ob1Diffuse = gameObjects(userValue1).materials.get(0).get(
            ColorAttribute.Diffuse).asInstanceOf[ColorAttribute]
          ob1Diffuse.color.set(Color.WHITE) // new Color(random(), random(), random(), 1f))
        }

        true
      }

    }
  }

  override def dispose() = {
//    contactListener.dispose()

    dynamicsWorld.dispose()
    constraintSolver.dispose()
    broadPhase.dispose()

    dispatcher.dispose()
    collisionConfig.dispose()

    constraints.foreach(_.dispose())

    gameObjects.foreach(_.dispose())
    gameObjectConstructors.foreach(_._2.dispose())

    modelBatch.dispose()
    model.dispose()
  }

  var angle = 90f
  var speed = 90f

  override def render() = {
    val delta = math.min(1f/30f, Gdx.graphics.getDeltaTime)

    angle = (angle + delta * speed) % 360f
    groundObject.transform.setTranslation(0, sinDeg(angle) * 2.5f, 0f)
//    groundObject.body.setWorldTransform(groundObject.transform) // not needed because bullet automatically uses motion info for this
//    groundObject.body.activate()

    dynamicsWorld.stepSimulation(delta, 5, 1f/60)

//    for (obj <- gameObjects) {
      //      println(s"Processing: ${obj.node} moving=${obj.moving}")
//      obj.transform.trn(0f, -delta*2, 0f)
//      obj.body.getWorldTransform(obj.transform)
      //      checkCollision(obj.body, groundObject.body)
//    }

//    collisionWorld.performDiscreteCollisionDetection()

    spawnTimer -= delta
    if (spawnTimer < 0) {
      val obj1 = spawn()
      val obj2 = spawn()
      val obj3 = spawn()
      val obj4 = spawn()

      addConstraint(obj1, obj2)
      addConstraint(obj2, obj3)
      addConstraint(obj3, obj4)
      spawnTimer = 1.5f
    }

    camController.update()

    Gdx.gl.glClearColor(0.3f, 0.3f, 0.3f, 1f)
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT)

    modelBatch.begin(cam)
    modelBatch.render(gameObjects, environment)
    modelBatch.end()

    debugDrawer.begin(cam)
    dynamicsWorld.debugDrawWorld()
    debugDrawer.end()
  }

  def spawn() = {

    val objName = gameObjectConstructorsIdx(random(gameObjectConstructorsIdx.size - 1))
    val obj= gameObjectConstructors(objName).build()
    obj.transform.setFromEulerAngles(random(360f), random(360f), random(350f))
    obj.transform.trn(random(-2.5f, 2.5f), 9f, random(-2.5f, 2.5f))
    obj.body.proceedToTransform(obj.transform)
    obj.body.setUserValue(gameObjects.size)
    obj.body.setCollisionFlags(obj.body.getCollisionFlags | btCollisionObject.CollisionFlags.CF_CUSTOM_MATERIAL_CALLBACK)
    gameObjects :+= obj

//    dynamicsWorld.addRigidBody(obj.body, OBJECT_FLAG, ALL_FLAG)
    dynamicsWorld.addRigidBody(obj.body)
    obj.body.setContactCallbackFlag(OBJECT_FLAG)
    obj.body.setContactCallbackFilter(GROUND_FLAG)

    obj
  }

  def addConstraint(obj1: GameObjectPhis, obj2: GameObjectPhis) = {
    val frameInA = new Matrix4()//.setTranslation(obj1.body.getCenterOfMassPosition)
    val frameInB = new Matrix4()//.setTranslation(obj2.body.getCenterOfMassPosition)

    val constraint = new btGeneric6DofSpringConstraint(obj1.body, obj2.body, frameInA, frameInA, true)

    val len = 3f

    constraint.setLinearLowerLimit(new Vector3(-len, -len, -len))
    constraint.setLinearUpperLimit(new Vector3( len,  len,  len))

    constraint.setDbgDrawSize(1f)


    constraint.enableSpring(0, true)
    constraint.setStiffness(0, 1)
    constraint.setDamping(0, 0.5f)
    constraint.setParam(btConstraintParams.BT_CONSTRAINT_STOP_CFM, 1.0e-5f, 5)
    constraint.setEquilibriumPoint()

    dynamicsWorld.addConstraint(constraint)

    constraints ::= constraint
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

class MyMotionState(val transform: Matrix4) extends btMotionState {
  def this() = this(new Matrix4())

  override def getWorldTransform(worldTrans: Matrix4) = worldTrans.set(transform)
  override def setWorldTransform(worldTrans: Matrix4) = transform.set(worldTrans)
}

class GameObjectPhis(model: Model, node: String,
                 constructionInfo: btRigidBody.btRigidBodyConstructionInfo)
    extends ModelInstance(model, node) with Disposable {

  val body = new btRigidBody(constructionInfo)
  val motionState = new MyMotionState(transform)
  body.setMotionState(motionState)

  override def dispose() = {
    body.dispose()
    motionState.dispose()
  }
}

object GameObjectPhis {


  class Builder(model: Model, node: String, shape: btCollisionShape, mass: Float) extends Disposable {
    val localInertia = new Vector3()

    if (mass > 0f)
      shape.calculateLocalInertia(mass, localInertia)
    else
      localInertia.setZero()

    val constructionInfo = new btRigidBodyConstructionInfo(mass, null, shape, localInertia)

    def build() = {
      new GameObjectPhis(model, node, constructionInfo)
    }

    override def dispose() = {
      shape.dispose()
      constructionInfo.dispose()
    }
  }
}