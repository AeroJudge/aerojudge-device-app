function disableCarouselTouchDrag(element) {
    var instance = M.Carousel.getInstance(element);
    // These bound handlers belong to the vendored Materialize carousel; recheck on upgrade.
    element.removeEventListener('touchstart', instance._handleCarouselTapBound);
    element.removeEventListener('touchmove', instance._handleCarouselDragBound);
    element.removeEventListener('touchend', instance._handleCarouselReleaseBound);
}
