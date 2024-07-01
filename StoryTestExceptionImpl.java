package solution;

import provided.StoryTestException;

public class StoryTestExceptionImpl extends StoryTestException
{
    String firstFailedSentence;
    String expected;
    String result;
    int numFails;

    /** Copy constructor **/
    public StoryTestExceptionImpl(StoryTesterImpl other)
    {
        this.firstFailedSentence = other.firstFailedSentence;
        this.expected = other.expected;
        this.result = other.result;
        this.numFails = other.numFails;
    }

    /**
     * Returns a string representing the sentence
     * of the first Then sentence that failed
     */
    @Override
    public String getSentance()
    {
        return firstFailedSentence;
    }

    /**
     * Returns a string representing the expected value from the story
     * of the first Then sentence that failed.
     */
    @Override
    public String getStoryExpected()
    {
        return expected;
    }

    /**
     * Returns a string representing the actual value.
     * of the first Then sentence that failed.
     */
    @Override
    public String getTestResult()
    {
        return result;
    }

    /**
     * Returns an int representing the number of Then sentences that failed.
     */
    @Override
    public int getNumFail()
    {
        return numFails;
    }
}
