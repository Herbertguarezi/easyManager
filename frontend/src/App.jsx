import Footer from "./Components/Footer"
import Header from "./Components/Header"

function App() {
  function handleClique(){
    fetch("http://localhost:8080/products")
    .then(r => r.json())
    .then(json => console.log(json))
  }

  return (
    <>
      <Header/>
      <h1>Herbert</h1>
      <button onClick={handleClique}>Clique</button>
      <Footer/>
    </>
  )
}

export default App
