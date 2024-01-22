
// #pattern-matching

static class OptimizedActorWithJava21 extends UntypedAbstractActor {
  public static class Msg1 {}

  public static class Msg2 {}

  public static class Msg3 {}

  @Override
  public void onReceive(Object msg) throws Exception {
    switch(msg) {
      case Msg1 msg -> receiveMsg1((Msg1) msg);
      case Msg2 msg -> receiveMsg2((Msg2) msg);
      case Msg3 msg -> receiveMsg3((Msg3) msg);
      default _ -> unhandled(msg);
    }
  }

  private void receiveMsg1(Msg1 msg) {
      // actual work
  }

  private void receiveMsg2(Msg2 msg) {
      // actual work
  }

  private void receiveMsg3(Msg3 msg) {
      // actual work
  }
}
// #pattern-matching