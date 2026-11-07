import useTests from '../context/useTests';
import { isHttpMethod, VALID_HTTP_METHODS, type Test } from '../types';

function CreateTestForm() {
  const { addTest } = useTests();

  const handleSubmit = (e: React.SubmitEvent<HTMLFormElement>) => {
    e.preventDefault();
    const form = e.currentTarget;
    if (form) {
      const httpMethod = (form.httpMethod as HTMLInputElement).value;
      if (!isHttpMethod(httpMethod)) {
        return;
      }

      const newTest: Test = {
        id: crypto.randomUUID(),
        testName: (form.testName as HTMLInputElement).value,
        targetUrl: (form.targetUrl as HTMLInputElement).value,
        httpMethod: httpMethod,
        durationInSeconds: parseInt((form.durationInSeconds as HTMLInputElement).value, 10),
        requestsPerSec: parseInt((form.requestsPerSec as HTMLInputElement).value, 10),
      };
      addTest(newTest);
    }
  };

  return (
    <form className="flex justify-start flex-col gap-4" onSubmit={handleSubmit}>
      <label htmlFor="testName">Test Name:</label>
      <input type="text" id="testName" name="testName" />

      <label htmlFor="targetUrl">Target URL:</label>
      <input type="text" id="targetUrl" name="targetUrl" />

      <label htmlFor="httpMethod">HTTP Method:</label>
      <select id="httpMethod" name="httpMethod">
        {VALID_HTTP_METHODS.map((method) => (
          <option key={method} value={method}>
            {method}
          </option>
        ))}
      </select>

      <label htmlFor="durationInSeconds">durationInSeconds:</label>
      <input type="text" id="durationInSeconds" name="durationInSeconds" />

      <label htmlFor="requestsPerSec">Requests/sec:</label>
      <input type="text" id="requestsPerSec" name="requestsPerSec" />

      <button type="submit">Create Test</button>
    </form>
  );
}

export default CreateTestForm;