package solution;

import org.junit.ComparisonFailure;
import provided.*;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class StoryTesterImpl implements StoryTester
{
    private Object objectBackup;

    String firstFailedSentence;
    String expected;
    String result;
    int numFails;

    /** Creates and returns a new instance of testClass **/
    private static Object createTestInstance(Class<?> testClass) throws Exception
    {
        Object testInstance;
        try {
            // TODO: Try constructing a new instance using the default constructor of testClass
            Constructor<?> constructor = testClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            testInstance = constructor.newInstance();
        } catch (Exception e) {
            // TODO: Inner classes case; Need to first create an instance of the enclosing class
            Class<?> enclosingClass = testClass.getEnclosingClass();
            Object enclosingInstance = createTestInstance(enclosingClass);
            Constructor<?> constructor = testClass.getDeclaredConstructor(enclosingClass);
            constructor.setAccessible(true);
            testInstance = constructor.newInstance(enclosingInstance);
        }

        return testInstance;
    }

    /** Returns true if c has a copy constructor, or false if it doesn't **/
    private boolean copyConstructorExists(Class<?> c)
    {
        try {
            c.getDeclaredConstructor(c);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    /** Assigns into objectBackup a backup of obj.
    /** See homework's pdf for more details on backing up and restoring **/
    private void backUpInstance(Object obj) throws Exception
    {
        Object res = createTestInstance(obj.getClass());
        Field[] fieldsArr = obj.getClass().getDeclaredFields();
        for(Field field : fieldsArr)
        {
            field.setAccessible(true);
            Object fieldObject = field.get(obj);
            if (fieldObject == null)
            {
                field.set(res, null);
                continue;
            }
            Class<?> fieldClass = fieldObject.getClass();

            if(fieldObject instanceof Cloneable)
            {
                // TODO: Case1 - Object in field is cloneable
                Method cloneMethod = fieldClass.getDeclaredMethod("clone");
                cloneMethod.setAccessible(true);
                field.set(res, cloneMethod.invoke(fieldObject));
            }
            else if(copyConstructorExists(fieldClass))
            {
                // TODO: Case2 - Object in field is not cloneable but copy constructor exists
                Constructor<?> constructor = fieldClass.getDeclaredConstructor(fieldClass);
                constructor.setAccessible(true);
                field.set(res, constructor.newInstance(fieldObject));
            }
            else
            {
                // TODO: Case3 - Object in field is not cloneable and copy constructor does not exist
                field.set(res, fieldObject);
            }
        }
        this.objectBackup = res;
    }

    /** Assigns into obj's fields the values in objectBackup fields.
    /** See homework's pdf for more details on backing up and restoring **/
    private void restoreInstance(Object obj) throws Exception
    {
        Field[] classFields = obj.getClass().getDeclaredFields();
        for (Field field : classFields)
        {
            // TODO: Complete.
            field.setAccessible(true);
            Object backupFieldValue = field.get(objectBackup);

            if (backupFieldValue == null)
            {
                field.set(obj, null);
                continue;
            }

            // TODO: Check if this fine
            // Here we perform a shallow copy to the pointers of the fields
            // Should be OK since we overwrite objectBackup as a whole
            field.set(obj, backupFieldValue);
        }
    }

    /** Returns the matching annotation class according to annotationName (Given, When or Then) **/
    private static Class<? extends Annotation> getAnnotationClass(String annotationName)
    {
        return switch (annotationName) {
            // TODO: Return matching annotation class
            case "Given" -> Given.class;
            case "When" -> When.class;
            case "Then" -> Then.class;
            default -> null;
        };

    }

    /** Invokes a method annotated with a specific annotation and sentence in the inheritance tree of testClass **/
    private static void invokeAnnotatedMethod(Class<?> testClass, Object testInstance,
                                                            String annotationName, String annotationSentence,
                                                            String parameter) throws Exception
    {
        Class<?> inspectedClass = testClass;
        while (inspectedClass != Object.class)
        {
            for (final Method method : inspectedClass.getDeclaredMethods())
            {
                boolean methodFound = false;
                if (method.isAnnotationPresent(getAnnotationClass(annotationName)))
                {
                    switch (annotationName)
                    {
                        case "Given":
                            Given annotationGiven = method.getAnnotation(Given.class);
                            if (Objects.equals(annotationGiven.value().substring(0, annotationGiven.value().lastIndexOf('&') - 1), annotationSentence))
                            {
                                methodFound = true;
                            }
                            break;
                        case "When":
                            When annotationWhen = method.getAnnotation(When.class);
                            if (Objects.equals(annotationWhen.value().substring(0, annotationWhen.value().lastIndexOf('&') - 1), annotationSentence))
                            {
                                methodFound = true;
                            }
                            break;
                        case "Then":
                            Then annotationThen = method.getAnnotation(Then.class);
                            if (Objects.equals(annotationThen.value().substring(0, annotationThen.value().lastIndexOf('&') - 1), annotationSentence))
                            {
                                methodFound = true;
                            }
                            break;
                    }

                    if (methodFound)
                    {
                        // Just in case the method is private we need to be able to invoke it
                        method.setAccessible(true);

                        try {
                            int parameterAsInteger = Integer.parseInt(parameter);
                            // parameter is a valid integer
                            if (method.getParameterTypes()[0].equals(Integer.class))
                            {
                                method.invoke(testInstance, parameterAsInteger);
                            }
                            else
                            {
                                method.invoke(testInstance, parameter);
                            }
                            return;
                        } catch (NumberFormatException | NullPointerException e) {
                            // sorry, not an integer, so the method has to take a string
                            if (method.getParameterTypes()[0].equals(String.class))
                            {
                                method.invoke(testInstance, parameter);
                                return;
                            }
                        }
                    }
                }
            }

            // Move to the upper class in the hierarchy in search for more methods
            inspectedClass = inspectedClass.getSuperclass();
        }

        if (annotationName.equals("Given"))
        {
            throw new GivenNotFoundException();
        }
        else if (annotationName.equals("When"))
        {
            throw new WhenNotFoundException();
        }
        else
        {
            throw new ThenNotFoundException();
        }
    }

    /** Test a story on the inheritance tree of testClass **/
    @Override
    public void testOnInheritanceTree(String story, Class<?> testClass) throws Exception
    {
        if((story == null) || testClass == null) throw new IllegalArgumentException();

        this.numFails = 0;
        Object testInstance = createTestInstance(testClass);

        boolean shouldPerformBackup = true;
        for(String sentence : story.split("\n"))
        {
            String[] words = sentence.split(" ", 2);

            String annotationName = words[0];

            String sentenceSub = words[1].substring(0, words[1].lastIndexOf(' ')); // Sentence without the parameter and annotation
            String parameter = sentence.substring(sentence.lastIndexOf(' ') + 1);

            // TODO: Complete.
            if (annotationName.equals("When"))
            {
                if (shouldPerformBackup)
                {
                    shouldPerformBackup = false;
                    backUpInstance(testInstance);
                }
            }
            else if (annotationName.equals("Then"))
            {
                // Since we met Then, we should perform a backup the next time we meet a When
                shouldPerformBackup = true;
            }

            try {
                invokeAnnotatedMethod(testClass, testInstance, annotationName, sentenceSub, parameter);

            } catch (InvocationTargetException e) {
                if (++this.numFails == 1)
                {
                    firstFailedSentence = sentence;
                    expected = ((ComparisonFailure) e.getCause()).getExpected();
                    result = ((ComparisonFailure) e.getCause()).getActual();
                }

                // After every 'Then' failure we have to restore our object from the backup, and keep running any other
                // sentences without rethrowing the comparison failure
                restoreInstance(testInstance);
            }
        }

        // TODO: Throw StoryTestExceptionImpl if the story failed.
        if (this.numFails > 0)
        {
            throw new StoryTestExceptionImpl(this);
        }
    }

    /** Test a story on the inheritance tree of testClass, or one of its recursive nested classes **/
    @Override
    public void testOnNestedClasses(String story, Class<?> testClass) throws Exception
    {
        // TODO: Complete.
        try {
            testOnInheritanceTree(story, testClass);
        } catch (GivenNotFoundException e) {
            boolean methodFound = false;
            final List<Class<?>> allNestedClasses = getRecursiveNestedClassesOf(testClass);
            for (final Class<?> nestedClass : allNestedClasses)
            {
                // testClass is now a nestedClass, so we have to create a new proper testInstance, but luckily enough
                // our well encapsulated code takes care of it
                try {
                    testOnInheritanceTree(story, nestedClass);
                    methodFound = true;
                    break; //We run the story only once, for a sole testClass
                } catch (GivenNotFoundException ex) { //We don't catch the other two, since we can assume only one nested class has the proper Given statement
                    // Do nothing, and just wait for the next for-loop iteration to find a proper nestedClass
                }
            }

            if (!methodFound)
            {
                // We rethrow the exception in one of two cases:
                //  1. There were no nested classes
                //  2. No nested class had a suitable function with a proper Given annotation
                throw e;
            }
        }
    }

    /** Recursively returns all nested classes of testClass **/
    private static List<Class <?>> getRecursiveNestedClassesOf(Class<?> testClass)
    {
        List<Class<?>> nestedClasses = new ArrayList<>();

        // Get all declared classes of the given class
        Class<?>[] declaredClasses = testClass.getDeclaredClasses();

        // Add declared classes to the nestedClasses list
        for (Class<?> declaredClass : declaredClasses)
        {
            nestedClasses.add(declaredClass);

            // Recursively get nested classes of the declared class
            nestedClasses.addAll(getRecursiveNestedClassesOf(declaredClass));
        }

        return nestedClasses;
    }
}
