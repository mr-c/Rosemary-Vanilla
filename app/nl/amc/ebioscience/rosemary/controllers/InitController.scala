/*
 * Copyright (C) 2016  Academic Medical Center of the University of Amsterdam (AMC)
 * 
 * This program is semi-free software: you can redistribute it and/or modify it
 * under the terms of the Rosemary license. You may obtain a copy of this
 * license at:
 * 
 * https://github.com/AMCeScience/Rosemary-Vanilla/blob/master/LICENSE.md
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * You should have received a copy of the Rosemary license
 * along with this program. If not, 
 * see https://github.com/AMCeScience/Rosemary-Vanilla/blob/master/LICENSE.md.
 * 
 *        Project: https://github.com/AMCeScience/Rosemary-Vanilla
 *        AMC eScience Website: http://www.ebioscience.amc.nl/
 */
package nl.amc.ebioscience.rosemary.controllers

import javax.inject._
import play.api.Logger
import play.api.mvc._
import scala.util.Random
import java.util.Date
import nl.amc.ebioscience.rosemary.models._
import nl.amc.ebioscience.rosemary.models.core._
import nl.amc.ebioscience.rosemary.models.core.ModelBase._
import nl.amc.ebioscience.rosemary.models.core.Implicits._
import nl.amc.ebioscience.processingmanager.types.ProcessingLifeCycle
import org.kohsuke.randname.RandomNameGenerator
import nl.amc.ebioscience.rosemary.services.CryptoService

/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class InitController @Inject() (cryptoService: CryptoService) extends Controller {

  def init = Action { implicit request =>
    playSalat.db().dropDatabase()

    // Create structure around the Datums
    initDB(request)

    // Inject Mock Data
    injectMockData

    // Inject Mock Application
    injectMockApplications

    // Inject Mock Processings
    injectMockProcessings

    Redirect("/reindex")
  }

  private def initDB(request: Request[AnyContent]) = {
    Logger.info("Initialising the database...")

    // Admin User
    val adminUser = User("admin@rosemary.ebioscience.amc.nl", "secret", "Admin Admin", true, true, Role.Admin).hashPassword.insert
    val adminWorkspace = WorkspaceTag("Admin's Workspace", Membered(adminUser.id)).insert

    // Test users
    // Approved, enabled user, and their workspace
    val approvedUser = User("approved-user@rosemary.ebioscience.amc.nl", "secret", "Approved User", true).hashPassword.insert
    val approvedWorkspace = WorkspaceTag("Approved User's Workspace", Membered(approvedUser.id)).insert
    // Unapproved, enabled user, and their workspace
    val unapprovedUser = User("unapproved-user@rosemary.ebioscience.amc.nl", "secret", "Unapproved User", false).hashPassword.insert
    val unapprovedWorkspace = WorkspaceTag("Unapproved User's Workspace", Membered(unapprovedUser.id)).insert
    // Approved, disabled user, and their workspace
    val disabledUser = User("disabled-user@rosemary.ebioscience.amc.nl", "secret", "Disabled User", true, false).hashPassword.insert
    val disabledWorkspace = WorkspaceTag("Disabled User's Workspace", Membered(disabledUser.id)).insert
    // Shared workspace with one owner and two members
    val multiWorkspace = WorkspaceTag("Multi-user Workspace", Membered(approvedUser.id, Set(disabledUser.id, adminUser.id))).insert

    // Datum Category Tags
    val category_grandfather = DatumCategoryTag(Tag.DatumCategories.GrandFather.toString).insert
    val category_father = DatumCategoryTag(Tag.DatumCategories.Father.toString).insert
    val category_child = DatumCategoryTag(Tag.DatumCategories.Child.toString).insert

    // Processing Category Tags
    val processing_category_dataprocessing = ProcessingCategoryTag(Tag.ProcessingCategories.DataProcessing.toString).insert

    // Processing Status Tags
    val processing_status_tag_inpreparation = ProcessingStatusTag(ProcessingLifeCycle.InPreparation.toString).insert
    val processing_status_tag_stagein = ProcessingStatusTag(ProcessingLifeCycle.StageIn.toString).insert
    val processing_status_tag_submitting = ProcessingStatusTag(ProcessingLifeCycle.Submitting.toString).insert
    val processing_status_tag_inprogress = ProcessingStatusTag(ProcessingLifeCycle.InProgress.toString).insert
    val processing_status_tag_onhold = ProcessingStatusTag(ProcessingLifeCycle.OnHold.toString).insert
    val processing_status_tag_stageout = ProcessingStatusTag(ProcessingLifeCycle.StageOut.toString).insert
    val processing_status_tag_done = ProcessingStatusTag(ProcessingLifeCycle.Done.toString).insert
    val processing_status_tag_aborted = ProcessingStatusTag(ProcessingLifeCycle.Aborted.toString).insert
    val processing_status_tag_unknown = ProcessingStatusTag(ProcessingLifeCycle.Unknown.toString).insert

    // Information about the deployment
    val hp = request.headers.toMap.get("Host").get.head.split(":")
    Logger.debug(s"Host Information= ${hp mkString " : "}")

    // MongoDB Resource for files stored in GridFS
    Resource(
      name = "Local MongoDB",
      kind = ResourceKind.Mongodb,
      protocol = "http",
      host = hp.head,
      port = if (hp.length > 1) hp.last.toInt else 80,
      basePath = Some("/api/v1/download")).insert

    // WebDAV Resource
    Resource(
      name = "WebDAV",
      kind = ResourceKind.Webdav,
      protocol = "http",
      host = "localhost",
      basePath = Some("/webdav/files"),
      username = Some("webdav"),
      password = Some(cryptoService.encrypt("secret"))).insert

    Logger.info("Done initialising the database.")
  }

  private def injectMockData = {
    import nl.amc.ebioscience.rosemary.models.core.ValunitConvertors._
    Logger.info("Injecting mock data into database...")

    // Get a user whose workspace we're going to fill
    val adminUser = User.find("admin@rosemary.ebioscience.amc.nl").get

    // Get the workspace
    val workspace = adminUser.getWorkspaceTagsHasAccess.filter(_.name == "Multi-user Workspace").head

    val childTag = Tag.getDatumCategory(Tag.DatumCategories.Child.toString).id
    val fatherTag = Tag.getDatumCategory(Tag.DatumCategories.Father.toString).id
    val grandFatherTag = Tag.getDatumCategory(Tag.DatumCategories.GrandFather.toString).id

    val resource = Resource.getLocalMongoResource

    val rnd = new RandomNameGenerator()

    val grandFathers = for {
      gfi <- 1 to 5
    } yield Datum(
      name = s"GrandFatherDatum_${gfi}",
      resource = Some(resource.id),
      tags = Set(workspace.id, grandFatherTag),
      info = Info(
        dict = Map(
          "gf/number" -> Random.nextInt(100).toString(),
          "gf/foobar" -> (if (Random.nextBoolean()) "foo" else "bar"),
          "gf/who" -> rnd.next()).toValunit)).insert

    grandFathers.map { grandFather =>
      val fathers = for {
        fi <- 1 to Random.nextInt(5)
      } yield Datum(
        name = s"FatherDatum_${fi}",
        resource = Some(resource.id),
        tags = Set(workspace.id, fatherTag),
        info = Info(
          dict = Map(
            "f/number" -> Random.nextInt(100).toString(),
            "f/foobar" -> (if (Random.nextBoolean()) "foo" else "bar"),
            "f/who" -> rnd.next()).toValunit,
          inheritedDict = grandFather.info.dict ++ grandFather.info.inheritedDict,
          ascendents = grandFather.info.ascendents ::: List(Catname(grandFather.getCategoryName.getOrElse("unknown"), grandFather.name)))).insert

      fathers.map { father =>
        val children = for {
          ci <- 1 to Random.nextInt(5)
        } yield Datum(
          name = s"ChildDatum_${ci}",
          resource = Some(resource.id),
          tags = Set(workspace.id, childTag),
          info = Info(
            dict = Map(
              "c/number" -> Random.nextInt(100).toString(),
              "c/foobar" -> (if (Random.nextBoolean()) "foo" else "bar"),
              "c/who" -> rnd.next()).toValunit,
            inheritedDict = father.info.dict ++ father.info.inheritedDict,
            ascendents = father.info.ascendents ::: List(Catname(father.getCategoryName.getOrElse("unknown"), father.name)))).insert

        father.copy(children = father.children ++ children.map(_.id).toSet).update
      }

      grandFather.copy(children = grandFather.children ++ fathers.map(_.id).toSet).update
    }

    Logger.info("Done injecting mock data into database.")
  }

  private def injectMockApplications = {
    Logger.info("Injecting mock applications into database...")

    // Mock application
    val mockPmIPorts = Set(
      AbstractPort(name = "Parameter One", kind = PortKind.Param),
      AbstractPort(name = "Input Data", kind = PortKind.File))
    val mockPmOPorts = Set(
      AbstractPort(name = "Output Data", kind = PortKind.File))
    val mockPmApp = PMApplication(iPorts = mockPmIPorts, oPorts = mockPmOPorts)
    val mockUiIPorts = Set(
      AbstractPort(name = "Input Data", kind = PortKind.Data),
      AbstractPort(name = "Parameter One", kind = PortKind.Param))
    Application(
      name = "Mock",
      description = "This is a Mock application",
      version = Some("1.0"),
      platform = Some("Dirac"),
      iPorts = mockUiIPorts,
      pmApplication = mockPmApp,
      transformer = "mockTransformer").insert

    Logger.info("Done injecting mock applications into database.")
  }

  private def injectMockProcessings = {
    Logger.info("Injecting mock processings into database...")

    // Get a user whose workspace we're going to fill
    val adminUser = User.find("admin@rosemary.ebioscience.amc.nl").get

    // Get the workspace
    val workspace = adminUser.getWorkspaceTagsHasAccess.filter(_.name == "Multi-user Workspace").head

    // Get Application
    val application = Recipe.findByType("Application").filter(_.name == "Mock").head.asInstanceOf[Application]

    // Get Tags
    val dataProcessingTag = Tag.getProcessingCategory(Tag.ProcessingCategories.DataProcessing.toString)
    val fatherCategory = Tag.datumCategoriesNameMap(Tag.DatumCategories.Father.toString)
    val childCategory = Tag.datumCategoriesNameMap(Tag.DatumCategories.Child.toString)

    // Get some Datums
    val fatherData = Datum.findWithAllTagsNoPage(Set(workspace.id, fatherCategory.id)).toSeq
    val childData = Datum.findWithAllTagsNoPage(Set(workspace.id, childCategory.id)).toSeq

    // Get Processing Statuses
    val seqStatuses = Seq(ProcessingLifeCycle.InProgress, ProcessingLifeCycle.OnHold, ProcessingLifeCycle.Aborted)
    val seqStatusTags = Seq(Tag.getProcessingStatusTag(ProcessingLifeCycle.InProgress.toString),
      Tag.getProcessingStatusTag(ProcessingLifeCycle.OnHold.toString),
      Tag.getProcessingStatusTag(ProcessingLifeCycle.Aborted.toString)).map(_.id)

    // Insert N processings with randomised metadata
    for (i <- 1 to 15) {
      val processingGroup = ProcessingGroup(
        name = s"Mock_${i}",
        initiator = adminUser.id,
        inputs = application.iPorts.toSeq.map { abstractPort =>
          val datum = fatherData(Random.nextInt(fatherData.size))
          ParamOrDatum(
            name = abstractPort.name,
            param = if (abstractPort.kind == PortKind.Param) Some(Random.alphanumeric.take(10).mkString) else None,
            datum = if (abstractPort.kind == PortKind.Data) Some(DatumAndReplica(datum = datum.id)) else None)
        },
        recipes = Set(application.id),
        tags = Set(workspace.id, dataProcessingTag.id, seqStatusTags(Random.nextInt(seqStatusTags.size))),
        progress = Random.nextInt(100),
        statuses = Seq(
          nl.amc.ebioscience.rosemary.models.Status(ProcessingLifeCycle.InPreparation),
          nl.amc.ebioscience.rosemary.models.Status(seqStatuses(Random.nextInt(seqStatuses.size)), new Date(new Date().getTime + Random.nextInt(300) * 60000)))).insert

      // Insert M submissions with randomised metadata
      for (j <- 1 to Random.nextInt(20)) {

        val someOfTheOPorts = application.pmApplication.oPorts.filter(_ => Random.nextBoolean)
        val finished = application.pmApplication.oPorts.size == someOfTheOPorts.size
        val statuses = Seq(
          nl.amc.ebioscience.rosemary.models.Status(ProcessingLifeCycle.InPreparation),
          nl.amc.ebioscience.rosemary.models.Status(seqStatuses(Random.nextInt(seqStatuses.size)), new Date(new Date().getTime + Random.nextInt(300) * 60000)))

        val processing = Processing(
          parentId = Some(processingGroup.id),
          name = s"Mock_${i}_${j}",
          initiator = adminUser.id,
          inputs = application.pmApplication.iPorts.map { abstractPort =>
            val datum = childData(Random.nextInt(childData.size))
            ParamOrDatum(
              name = abstractPort.name,
              param = if (abstractPort.kind == PortKind.Param) Some(Random.alphanumeric.take(10).mkString) else None,
              datum = if (abstractPort.kind == PortKind.File) Some(DatumAndReplica(datum = datum.id)) else None)
          },
          outputs = someOfTheOPorts.map { abstractPort =>
            val datum = childData(Random.nextInt(childData.size))
            ParamOrDatum(
              name = abstractPort.name,
              param = if (abstractPort.kind == PortKind.Param) Some(Random.alphanumeric.take(10).mkString) else None,
              datum = if (abstractPort.kind == PortKind.File) Some(DatumAndReplica(datum = datum.id)) else None)
          },
          recipes = Set(application.id),
          tags = Set(workspace.id, dataProcessingTag.id, seqStatusTags(Random.nextInt(seqStatusTags.size))),
          progress = if (finished) 100 else Random.nextInt(91),
          statuses = if (finished) statuses :+ nl.amc.ebioscience.rosemary.models.Status(ProcessingLifeCycle.Done, new Date(new Date().getTime + 18000000 + Random.nextInt(300) * 60000)) else statuses)
        ProcessingGroup.processings.insert(processing)
      }
    }

    Logger.info("Done injecting mock processings into database.")
  }

}