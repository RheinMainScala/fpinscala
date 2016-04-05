package fpinscala.parallelism

import org.specs2.mutable._
import org.specs2.specification.AllExpectations
import org.specs2.matcher.DataTables
import org.specs2.runner.JUnitRunner
import org.junit.runner.RunWith
import java.util.concurrent._
import org.specs2.matcher.Matcher
import fpinscala.parallelism.Utils._

@RunWith(classOf[JUnitRunner])
class ParSpec extends Specification with DataTables {
  // Run the tests sequentially as these tests assert also the time it takes to run them.
  sequential

  //val maxNbOfThreadsInTests = 8
  //val es = Executors.newFixedThreadPool(maxNbOfThreadsInTests)
  val es = Executors.newCachedThreadPool()
  
    // Test if a duration is in an interval.
  def be_around(duration: Long, dp: Double = durationPrecision): Matcher[Long] = {
    val i = (duration * dp).toLong
    be_>(duration - i) and be_<(duration + i)
  }

  "The following exercises should be correct" >> {

    "Exercise 7.3: map2 that respects the contract of timeouts on Future." in {
      "callableA duration" || "callableB duration" || "Wait until" || "exception thrown" |
        d1 !! d2 !! safeMaxFor(d2) !! false |
        d2 !! d1 !! safeMaxFor(d2) !! false |
        d3 !! d2 !! safeMaxFor(d3) !! false |
        d3 !! d2 !! safeMaxFor(d2) !! true | // safeMaxFor(d2) < safeMinFor(d3)
        d2 !! d3 !! safeMaxFor(d2) !! true |> {
          (durationA, durationB, waitingDuration, exceptionToBeThrown) =>
            def parA(es: ExecutorService): Future[CA] = es.submit(CallableCA(durationA))
            def parB(es: ExecutorService): Future[CB] = es.submit(CallableCB(durationB))
            //val parC = Par.map2(parA, parB)((a, b) => CC(a + "/" + b))
            val parC = Par.map2WithTimeOut(parA, parB)((a, b) => CC(a + "/" + b))
            try {
              val res = parC(es).get(waitingDuration, TimeUnit.MILLISECONDS)
              if (exceptionToBeThrown)
                ko("A TimeoutException should have been thrown!")
              else
                res must_== CC(s"CA(${durationA}ms)/CB(${durationB}ms)")
            } catch {
              case te: TimeoutException if (exceptionToBeThrown) => ok
              case ex: Exception                                 => ko(ex.getMessage())
            }
        }
    }

    "Exercise 7.4: asyncF." in {
      def f(a: CA) = CB(a.a)
      val par = Par.asyncF(f)
      par(CA("a"))(es).get must_== CB("a")
    }

    "Exercise 7.5: sequence." in {
      val parA = (es: ExecutorService) => es.submit(CallableCA(d1, "1-"))
      val parB = (es: ExecutorService) => es.submit(CallableCA(d1, "2-"))
      val parC = (es: ExecutorService) => es.submit(CallableCA(d1, "3-"))
      val ps = List(parA, parB, parC)
      val parList = Par.sequence(ps)

      try {
        // The execution time must be around d1 ms.
        val res = parList(es).get(safeMaxFor(d1), TimeUnit.MILLISECONDS)
        res must_== List(CA(s"1-${d1}ms"), CA(s"2-${d1}ms"), CA(s"3-${d1}ms"))
      } catch {
        case ex: Throwable => ko(ex.getMessage())
      }
    }
  }

  "Exercise 7.6: parFilter." in {
    // Note: With a newFixedThreadPool and if the list contains more than 5 elements the test hangs.
    // For more information why, read the paragraph about deadlocks and thread pools of bounded size. 
    val as = List(CA("1"), CA("2"), CA("3"), CA("4"), CA("5"), CA("6"), CA("7"), CA("8"))
    val parList = Par.parFilter(as) { ca =>
      Thread.sleep(d2)
      ca.a == "1" || ca.a == "8"
    }

    try {
      val res = parList(es).get(safeMaxFor(d2), TimeUnit.MILLISECONDS)
      res must_== List(CA("1"), CA("8"))
    } catch {
      case ex: Throwable => ko(ex.getMessage())
    }
  }

  "Exercise 7.11: choiceN." in {
    val callableN = new Callable[Int] {
      def call(): Int = { Thread.sleep(d1); 1 }
    }

    val n = (es: ExecutorService) => es.submit(callableN)
    val parA = (es: ExecutorService) => es.submit(CallableCA(tooLongDuration, "A-"))
    val parB = (es: ExecutorService) => es.submit(CallableCA(d2, "B-"))
    val parC = (es: ExecutorService) => es.submit(CallableCA(tooLongDuration, "C-"))
    val parD = (es: ExecutorService) => es.submit(CallableCA(tooLongDuration, "D-"))

    val t0 = System.currentTimeMillis()
    val parChoiceN = Par.choiceN(n)(List(parA, parB, parC, parD))
    val res = parChoiceN(es).get
    val t1 = System.currentTimeMillis()
    val duration = t1 - t0
    (res must_== CA(s"B-${d2}ms")) and (duration must be_around(d1 + d2))
  }

  "Exercise 7.11: choice and terms of choiceN." in {
    val callableChoice = new Callable[Boolean] {
      def call(): Boolean = {
        Thread.sleep(d1)
        true
      }
    }
    val par1 = (es: ExecutorService) => es.submit(CallableCA(tooLongDuration, "C-"))
    val par2 = (es: ExecutorService) => es.submit(CallableCA(d2, "D-"))
    val cond = (es: ExecutorService) => es.submit(callableChoice)

    val t0 = System.currentTimeMillis()
    val parChoice = Par.choiceViaChoiceN(cond)(par2, par1)
    val res = parChoice(es).get
    val t1 = System.currentTimeMillis()
    val duration = t1 - t0
    (res must_== CA(s"D-${d2}ms")) and (duration must be_around(d1 + d2))
  }

  "Exercise 7.13: chooser." in {
    import Par._
    val callableChooser = new Callable[CA] {
      def call(): CA = {
        Thread.sleep(d2)
        CA("a-")
      }
    }

    def choices(ca: CA): Par[CB] = (es: ExecutorService) => es.submit(CallableCB(d3, ca.a))
    val pa = (es: ExecutorService) => es.submit(callableChooser)

    val t0 = System.currentTimeMillis()
    val parChooser = Par.chooser(pa)(choices)
    val res = parChooser(es).get
    val t1 = System.currentTimeMillis()
    (res must_== CB(s"a-${d3}ms")) and ((t1 - t0) must be_around(d2 + d3))
  }

  "Exercise 7.13: choiceNViaChooser." in {
    val callableN = new Callable[Int] {
      def call(): Int = { Thread.sleep(d1); 1 }
    }

    val n = (es: ExecutorService) => es.submit(callableN)
    val parA = (es: ExecutorService) => es.submit(CallableCA(tooLongDuration, "A-"))
    val parB = (es: ExecutorService) => es.submit(CallableCA(d2, "B-"))
    val parC = (es: ExecutorService) => es.submit(CallableCA(tooLongDuration, "C-"))
    val parD = (es: ExecutorService) => es.submit(CallableCA(tooLongDuration, "D-"))

    val t0 = System.currentTimeMillis()
    val parChoiceN = Par.choiceNViaChooser(n)(List(parA, parB, parC, parD))
    val res = parChoiceN(es).get
    val t1 = System.currentTimeMillis()
    val duration = t1 - t0
    (res must_== CA(s"B-${d2}ms")) and (duration must be_around(d1 + d2))
  }

  "Exercise 7.13: choiceViaChooser." in {
    val callableChoice = new Callable[Boolean] {
      def call(): Boolean = {
        Thread.sleep(d1)
        true
      }
    }
    val par1 = (es: ExecutorService) => es.submit(CallableCA(tooLongDuration, "C-"))
    val par2 = (es: ExecutorService) => es.submit(CallableCA(d3, "D-"))
    val cond = (es: ExecutorService) => es.submit(callableChoice)

    val t0 = System.currentTimeMillis()
    val parChoice = Par.choiceViaChooser(cond)(par2, par1)
    val res = parChoice(es).get
    val t1 = System.currentTimeMillis()
    val duration = t1 - t0
    (res must_== CA(s"D-${d3}ms")) and (duration must be_around(d1 + d3))
  }

  "Exercise 7.14: join." in {
    import Par._
    val callablePar = new Callable[Par[CA]] {
      def call(): Par[CA] = {
        Thread.sleep(d2)
        val parA = (es: ExecutorService) => es.submit(CallableCA(d3, "A-"))
        parA
      }
    }
    val parPar = (es: ExecutorService) => es.submit(callablePar)
    val t0 = System.currentTimeMillis()
    val parJoin = join(parPar)
    val res = parJoin(es).get
    val t1 = System.currentTimeMillis()
    val duration = t1 - t0
    (res must_== CA(s"A-${d3}ms")) and (duration must be_around(d2 + d3))
  }

  "Exercise 7.14: joinViaFlatMap." in {
    import Par._
    val callablePar = new Callable[Par[CA]] {
      def call(): Par[CA] = {
        Thread.sleep(d1)
        val parA = (es: ExecutorService) => es.submit(CallableCA(d2, "A-"))
        parA
      }
    }
    val parPar = (es: ExecutorService) => es.submit(callablePar)
    val t0 = System.currentTimeMillis()
    val parJoin = joinViaFlatMap(parPar)
    val res = parJoin(es).get
    val t1 = System.currentTimeMillis()
    val duration = t1 - t0
    (res must_== CA(s"A-${d2}ms")) and (duration must be_around(d1 + d2))

  }

  "Exercise 7.14: flatMapViaJoin." in {
    val callableN = new Callable[Int] {
      def call(): Int = {
        Thread.sleep(d1)
        1
      }
    }
    val n = (es: ExecutorService) => es.submit(callableN)
    val parA = (es: ExecutorService) => es.submit(CallableCA(tooLongDuration, "A-"))
    val parB = (es: ExecutorService) => es.submit(CallableCA(d2, "B-"))
    val parC = (es: ExecutorService) => es.submit(CallableCA(tooLongDuration, "C-"))
    val parD = (es: ExecutorService) => es.submit(CallableCA(tooLongDuration, "D-"))

    val t0 = System.currentTimeMillis()
    val parFlatMap = Par.flatMapViaJoin(n)(List(parA, parB, parC, parD))
    val res = parFlatMap(es).get
    val t1 = System.currentTimeMillis()
    (res must_== CA(s"B-${d2}ms")) and ((t1 - t0) must be_around(d1 + d2))
  }

}