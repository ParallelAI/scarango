package com.outr.arango.api.model

import io.circe.Json


case class GeneralGraphEdgeDefinitionAddHttpExamplesRc400(error: Boolean,
                                                          code: Option[Int] = None,
                                                          errorMessage: Option[String] = None,
                                                          errorNum: Option[Int] = None)