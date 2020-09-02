package spec

import com.outr.arango.api.OperationType
import com.outr.arango._
import com.outr.arango.query._
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import profig.Profig

import scala.concurrent.duration._

class MaterializedSpec extends AsyncWordSpec with Matchers with Eventually {
  "Materialized" should {
    val ec = scala.concurrent.ExecutionContext.global
    lazy val walMonitor = database.wal.monitor(delay = 100.millis)(ec)
    lazy val userMonitor = database.users.monitor(walMonitor)
    lazy val locationMonitor = database.locations.monitor(walMonitor)

    val u1 = User("User 1", 21)
    val l1 = Location(u1._id, "San Jose", "California")

    def query(userIds: Query): Query = {
      val query =
        aqlu"""
              FOR u IN ${database.users}
              FILTER u._id IN userIds
              LET l = (
                FOR loc IN ${database.locations}
                FILTER loc.${Location.userId} IN userIds
                RETURN loc
              )
              INSERT {
                _key: u._key,
                ${MaterializedUser.name}: u.${User.name},
                ${MaterializedUser.age}: u.${User.age},
                ${MaterializedUser.locations}: l
              } INTO ${database.materializedUsers} OPTIONS { overwrite: true, waitForSync: true }
            """
      userIds + query
    }

    "initialize configuration" in {
      Profig.initConfiguration().map { _ =>
        succeed
      }
    }
    "initialize the database" in {
      database.init().map { _ =>
        succeed
      }
    }
    "temporary hack to represent materialization" in {
      userMonitor.attach { op =>
        op._key.foreach { userKey =>
          val userId = User.id(userKey)
          if (op.`type` == OperationType.InsertReplaceDocument) {
            val userIds = aqlu"LET userIds = [$userId]"
            database.query(query(userIds)).update(ec)
          } else if (op.`type` == OperationType.RemoveDocument) {
            database.materializedUsers.deleteOne(MaterializedUser.id(userId.value))
          }
        }
      }
      locationMonitor.attach { op =>
        op._key.foreach { locationKey =>
          val locationId = Location.id(locationKey)
          if (op.`type` == OperationType.InsertReplaceDocument) {
            val userIds =
              aqlu"""
                     LET newLoc = DOCUMENT($locationId)
                     LET userIds = [newLoc.userId]
                  """
            database.query(query(userIds)).update(ec)
          } else if (op.`type` == OperationType.RemoveDocument) {
            val userIds =
              aqlu"""
                     LET userIds = (
                      FOR m IN ${database.materializedUsers}
                      FILTER $locationId IN m.locations[*]._id
                      RETURN CONCAT('users/', m._key)
                    )
                  """
            database.query(query(userIds)).update(ec)
          }
        }
      }
      walMonitor.nextTick.flatMap { _ =>
        walMonitor.nextTick.map { _ =>
          succeed
        }
      }
    }
    "insert a user and verify it exists in materialized" in {
      database.users.insertOne(u1).flatMap { _ =>
        walMonitor.nextTick.flatMap { _ =>
          database.materializedUsers.all.results.map { list =>
            list should be(List(MaterializedUser(u1.name, u1.age, Nil, MaterializedUser.id(u1._id.value))))
          }
        }
      }
    }
    "insert a location and verify it was added to the materialized" in {
      database.locations.insertOne(l1).flatMap { _ =>
        walMonitor.nextTick.flatMap { _ =>
          database.materializedUsers.all.results.map { list =>
            list should be(List(MaterializedUser(u1.name, u1.age, List(l1), MaterializedUser.id(u1._id.value))))
          }
        }
      }
    }
    "delete a location and verify it was deleted from the materialized" in {
      database.locations.deleteOne(l1._id).flatMap { _ =>
        walMonitor.nextTick.flatMap { _ =>
          database.materializedUsers.all.results.map { list =>
            list should be(List(MaterializedUser(u1.name, u1.age, Nil, MaterializedUser.id(u1._id.value))))
          }
        }
      }
    }
    // TODO: Add another user
    "delete a user and verify it was deleted from materialized" in {
      database.users.deleteOne(u1._id).flatMap { _ =>
        walMonitor.nextTick.flatMap { _ =>
          database.materializedUsers.all.results.map { list =>
            list should be(Nil)
          }
        }
      }
    }
    "cleanup" in {
      val nextTick = walMonitor.nextTick
      walMonitor.stop()
      for {
        _ <- nextTick
        _ <- database.drop()
      } yield {
        succeed
      }
    }
  }

  object database extends Graph("materializedSpec") {
    val users: DocumentCollection[User] = vertex[User]
    val locations: DocumentCollection[Location] = vertex[Location]
    val materializedUsers: DocumentCollection[MaterializedUser] = vertex[MaterializedUser]
//    val materializedUsers: DocumentCollection[MaterializedUser] = materialized[MaterializedUser](
//      User,
//      MaterializedUser.name -> User.name,
//      MaterializedUser.age -> User.age,
//      MaterializedUser.locations -> Location.userId
//    )
  }

  case class User(name: String, age: Int, _id: Id[User] = User.id()) extends Document[User]
  object User extends DocumentModel[User] {
    val name: Field[String] = field("name")
    val age: Field[Int] = field("age")

    override val collectionName: String = "users"
    override implicit val serialization: Serialization[User] = Serialization.auto[User]

    override def indexes: List[Index] = Nil
  }

  case class Location(userId: Id[User], city: String, state: String, _id: Id[Location] = Location.id()) extends Document[Location]
  object Location extends DocumentModel[Location] {
    val userId: Field[Id[User]] = field("userId")
    val city: Field[String] = field("city")
    val state: Field[String] = field("state")

    override val collectionName: String = "locations"
    override implicit val serialization: Serialization[Location] = Serialization.auto[Location]

    override def indexes: List[Index] = Nil
  }

  case class MaterializedUser(name: String, age: Int, locations: List[Location], _id: Id[MaterializedUser]) extends Document[MaterializedUser]
  object MaterializedUser extends DocumentModel[MaterializedUser] {
    val name: Field[String] = field("name")
    val age: Field[Int] = field("age")
    val locations: Field[List[Location]] = field("locations")

    override val collectionName: String = "materializedUsers"
    override implicit val serialization: Serialization[MaterializedUser] = Serialization.auto[MaterializedUser]

    override def indexes: List[Index] = Nil
  }
}