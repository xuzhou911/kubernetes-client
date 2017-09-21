package com.goyeau.kubernetesclient

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri
import io.circe._
import io.circe.generic.auto._
import io.k8s.api.autoscaling.v1.{HorizontalPodAutoscaler, HorizontalPodAutoscalerList}

private[kubernetesclient] case class HorizontalPodAutoscalersOperations(protected val config: KubeConfig)(
  implicit protected val system: ActorSystem,
  protected val decoder: Decoder[HorizontalPodAutoscalerList]
) extends Listable[HorizontalPodAutoscalerList] {
  protected val resourceUri = s"${config.server}/apis/autoscaling/v1/horizontalpodautoscalers"

  def namespace(namespace: String) = NamespacedHorizontalPodAutoscalersOperations(config, namespace)
}

private[kubernetesclient] case class NamespacedHorizontalPodAutoscalersOperations(protected val config: KubeConfig,
                                                                                  protected val namespace: String)(
  implicit protected val system: ActorSystem,
  protected val encoder: Encoder[HorizontalPodAutoscaler],
  protected val decoder: Decoder[HorizontalPodAutoscalerList]
) extends Creatable[HorizontalPodAutoscaler]
    with Listable[HorizontalPodAutoscalerList]
    with GroupDeletable {
  protected val resourceUri = s"${config.server}/apis/autoscaling/v1/namespaces/$namespace/horizontalpodautoscalers"

  def apply(horizontalPodAutoscalerName: String) =
    HorizontalPodAutoscalerOperations(config, s"$resourceUri/$horizontalPodAutoscalerName")
}

private[kubernetesclient] case class HorizontalPodAutoscalerOperations(protected val config: KubeConfig,
                                                                       protected val resourceUri: Uri)(
  implicit protected val system: ActorSystem,
  protected val encoder: Encoder[HorizontalPodAutoscaler],
  protected val decoder: Decoder[HorizontalPodAutoscaler]
) extends Gettable[HorizontalPodAutoscaler]
    with Replaceable[HorizontalPodAutoscaler]
    with Deletable
